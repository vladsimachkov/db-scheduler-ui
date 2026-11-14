/*
 * Copyright (C) Bekk
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package no.bekk.dbscheduler.ui.sql.exec;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import no.bekk.dbscheduler.ui.sql.config.DbSchedulerUiSqlProperties;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.model.SqlJobExecution;
import no.bekk.dbscheduler.ui.sql.model.TriggerType;
import no.bekk.dbscheduler.ui.sql.pool.ConnectionPoolManager;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.repo.SqlJobExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Runs a SQL job against its target database and always records the outcome in {@code
 * sql_job_executions}, success or failure. On failure the exception is rethrown so db-scheduler
 * marks the execution failed and applies its normal failure handling.
 *
 * <p>Statements run with {@code autoCommit=true} — Trino and Impala have no cross-statement
 * transactions anyway, and each statement's effect should survive a later failure in the script.
 */
public class SqlJobExecutor implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(SqlJobExecutor.class);
  private static final int ERROR_MESSAGE_MAX_LENGTH = 4000;
  private static final long WATCHDOG_GRACE_SECONDS = 5;

  private final SqlConnectionRepository connections;
  private final SqlJobExecutionRepository executions;
  private final ConnectionPoolManager pools;
  private final DbSchedulerUiSqlProperties properties;
  private final ScheduledExecutorService watchdog =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "sql-job-watchdog");
            t.setDaemon(true);
            return t;
          });

  public SqlJobExecutor(
      SqlConnectionRepository connections,
      SqlJobExecutionRepository executions,
      ConnectionPoolManager pools,
      DbSchedulerUiSqlProperties properties) {
    this.connections = connections;
    this.executions = executions;
    this.pools = pools;
    this.properties = properties;
  }

  public SqlJobExecution execute(SqlJobData data, String taskInstance, TriggerType trigger) {
    SqlJobConnection connection = connections.findById(data.getConnectionId()).orElse(null);
    if (connection == null) {
      // Still record the failure so the job's history shows why it stopped working
      recordUnknownConnectionFailure(data, taskInstance, trigger);
      throw new SqlJobExecutionException(
          "SQL job " + data.getJobId() + " references unknown connection " + data.getConnectionId(),
          null);
    }

    int timeoutSeconds =
        Math.max(
            1,
            data.getTimeoutSeconds() > 0
                ? data.getTimeoutSeconds()
                : properties.defaultTimeoutSeconds());
    List<String> scripts = data.getScripts();

    Instant started = Instant.now();
    int executed = 0;
    long rows = 0;
    boolean capped = false;
    int scriptIndex = 0;
    try (Connection jdbc = pools.poolFor(connection).getConnection()) {
      jdbc.setAutoCommit(true);
      // Scripts run strictly in order; a failing statement stops everything (fail-fast),
      // leaving earlier statements' effects committed (autoCommit per statement).
      for (String script : scripts) {
        scriptIndex++;
        for (String sql : SqlScriptSplitter.split(script, connection.dialect())) {
          try (Statement statement = jdbc.createStatement()) {
            statement.setQueryTimeout(timeoutSeconds);
            ScheduledFuture<?> cancel = armWatchdog(statement, timeoutSeconds);
            try {
              boolean hasResultSet = statement.execute(sql);
              if (hasResultSet) {
                long[] counted = countRows(statement, capped);
                rows += counted[0];
                capped = capped || counted[1] == 1;
              } else {
                int updateCount = statement.getUpdateCount();
                if (updateCount > 0) {
                  rows += updateCount;
                }
              }
            } finally {
              cancel.cancel(false);
            }
            executed++;
          }
        }
      }
      SqlJobExecution result =
          record(
              data,
              taskInstance,
              connection.id(),
              connection.dialect(),
              started,
              executed,
              rows,
              capped,
              true,
              null,
              null,
              trigger);
      log.info(
          "SQL job {} finished: {} statements, {} rows in {} ms",
          data.getJobId(),
          executed,
          rows,
          result.durationMs());
      return result;
    } catch (SQLException | RuntimeException e) {
      record(
          data,
          taskInstance,
          connection.id(),
          connection.dialect(),
          started,
          executed,
          rows,
          capped,
          false,
          e.getClass().getName(),
          truncate(e.getMessage()),
          trigger);
      String where =
          scripts.size() > 1 ? " (script " + scriptIndex + " of " + scripts.size() + ")" : "";
      throw new SqlJobExecutionException(
          "SQL job " + data.getJobId() + " failed after " + executed + " statement(s)" + where, e);
    }
  }

  private long[] countRows(Statement statement, boolean alreadyCapped) throws SQLException {
    long counted = 0;
    try (ResultSet rs = statement.getResultSet()) {
      while (rs.next()) {
        counted++;
        if (counted >= properties.maxRowsPerStatement()) {
          return new long[] {counted, alreadyCapped ? 0 : 1};
        }
      }
    }
    return new long[] {counted, 0};
  }

  /**
   * Some drivers (notably Hive/Impala) ignore {@code setQueryTimeout}; the watchdog cancels the
   * statement after the timeout plus a grace period regardless.
   */
  private ScheduledFuture<?> armWatchdog(Statement statement, int timeoutSeconds) {
    long killAfter = timeoutSeconds + WATCHDOG_GRACE_SECONDS;
    return watchdog.schedule(
        () -> {
          try {
            log.warn("Cancelling statement via watchdog after {}s", killAfter);
            statement.cancel();
          } catch (SQLException | RuntimeException e) {
            log.warn("Watchdog failed to cancel statement", e);
          }
        },
        killAfter,
        TimeUnit.SECONDS);
  }

  private SqlJobExecution record(
      SqlJobData data,
      String taskInstance,
      String connectionId,
      SqlDialect dialect,
      Instant started,
      int executed,
      long rows,
      boolean capped,
      boolean succeeded,
      String errorClass,
      String errorMessage,
      TriggerType trigger) {
    Instant finished = Instant.now();
    SqlJobExecution execution =
        new SqlJobExecution(
            IdGenerator.next(),
            data.getJobId(),
            taskInstance,
            connectionId,
            dialect,
            String.join("\n\n", data.getScripts()),
            started,
            finished,
            succeeded,
            executed,
            rows,
            capped,
            finished.toEpochMilli() - started.toEpochMilli(),
            errorClass,
            errorMessage,
            trigger);
    try {
      executions.insert(execution);
    } catch (RuntimeException e) {
      // Never let history-recording failures mask the job outcome
      log.error("Failed to record execution of SQL job {}", data.getJobId(), e);
    }
    return execution;
  }

  private void recordUnknownConnectionFailure(
      SqlJobData data, String taskInstance, TriggerType trigger) {
    Instant now = Instant.now();
    record(
        data,
        taskInstance,
        data.getConnectionId(),
        null, // dialect unknown: the connection row is gone
        now,
        0,
        0,
        false,
        false,
        SqlJobExecutionException.class.getName(),
        "Connection " + data.getConnectionId() + " no longer exists",
        trigger);
  }

  private static String truncate(String message) {
    if (message == null) {
      return null;
    }
    return message.length() <= ERROR_MESSAGE_MAX_LENGTH
        ? message
        : message.substring(0, ERROR_MESSAGE_MAX_LENGTH) + "...";
  }

  @Override
  public void destroy() {
    watchdog.shutdownNow();
  }
}
