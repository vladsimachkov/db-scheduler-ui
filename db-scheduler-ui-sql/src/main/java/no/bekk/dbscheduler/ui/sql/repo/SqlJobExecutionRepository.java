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
package no.bekk.dbscheduler.ui.sql.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobExecution;
import no.bekk.dbscheduler.ui.sql.model.TriggerType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Execution history for SQL jobs, stored in {@code sql_job_executions}. */
public class SqlJobExecutionRepository {

  private static final String COLUMNS =
      "id, job_id, task_instance, connection_id, dialect, script_text, time_started, time_finished,"
          + " succeeded, statements_executed, rows_affected, rows_capped, duration_ms,"
          + " error_class, error_message, triggered_by";

  private final JdbcTemplate jdbc;

  public SqlJobExecutionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(SqlJobExecution execution) {
    jdbc.update(
        "insert into sql_job_executions (" + COLUMNS + ") values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
        execution.id(),
        execution.jobId(),
        execution.taskInstance(),
        execution.connectionId(),
        execution.dialect() == null ? null : execution.dialect().name(),
        execution.scriptText(),
        Timestamp.from(execution.timeStarted()),
        Timestamp.from(execution.timeFinished()),
        execution.succeeded(),
        execution.statementsExecuted(),
        execution.rowsAffected(),
        execution.rowsCapped(),
        execution.durationMs(),
        execution.errorClass(),
        execution.errorMessage(),
        execution.triggeredBy().name());
  }

  public List<SqlJobExecution> findRecentByJob(String jobId, int limit) {
    // Portable top-N: LIMIT (MySQL) and FETCH FIRST (H2/PostgreSQL/Oracle/MSSQL) are not
    // interchangeable, so cap rows driver-side with setMaxRows instead of dialect SQL.
    return jdbc.query(
        "select "
            + COLUMNS
            + " from sql_job_executions where job_id = ? order by time_started desc, id desc",
        ps -> {
          ps.setString(1, jobId);
          ps.setMaxRows(limit);
        },
        this::map);
  }

  private SqlJobExecution map(ResultSet rs, int rowNum) throws SQLException {
    return new SqlJobExecution(
        rs.getLong("id"),
        rs.getString("job_id"),
        rs.getString("task_instance"),
        rs.getString("connection_id"),
        parseDialect(rs.getString("dialect")),
        rs.getString("script_text"),
        toInstant(rs.getTimestamp("time_started")),
        toInstant(rs.getTimestamp("time_finished")),
        rs.getBoolean("succeeded"),
        rs.getInt("statements_executed"),
        rs.getLong("rows_affected"),
        rs.getBoolean("rows_capped"),
        rs.getLong("duration_ms"),
        rs.getString("error_class"),
        rs.getString("error_message"),
        TriggerType.valueOf(rs.getString("triggered_by")));
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static SqlDialect parseDialect(String value) {
    return value == null ? null : SqlDialect.valueOf(value);
  }
}
