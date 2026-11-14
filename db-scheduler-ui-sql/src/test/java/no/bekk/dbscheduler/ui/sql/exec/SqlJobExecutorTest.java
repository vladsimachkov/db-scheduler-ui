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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import no.bekk.dbscheduler.ui.sql.SqlTestSchema;
import no.bekk.dbscheduler.ui.sql.config.DbSchedulerUiSqlProperties;
import no.bekk.dbscheduler.ui.sql.crypto.CredentialCipher;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.model.SqlJobExecution;
import no.bekk.dbscheduler.ui.sql.model.TriggerType;
import no.bekk.dbscheduler.ui.sql.pool.ConnectionPoolManager;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.repo.SqlJobExecutionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** Runs real SQL against an in-memory H2 database standing in as the target (dialect GENERIC). */
class SqlJobExecutorTest {

  private SqlConnectionRepository connections;
  private SqlJobExecutionRepository executions;
  private ConnectionPoolManager pools;
  private JdbcTemplate target;

  @BeforeEach
  void setUp() {
    DataSource store = SqlTestSchema.inMemoryStore("exec_store_" + System.nanoTime());
    SqlTestSchema.applyH2Ddl(store);
    JdbcTemplate storeJdbc = new JdbcTemplate(store);
    connections = new SqlConnectionRepository(storeJdbc);
    executions = new SqlJobExecutionRepository(storeJdbc);
    pools = new ConnectionPoolManager(new CredentialCipher(null));

    String targetName = "exec_target_" + System.nanoTime();
    DataSource targetDb = SqlTestSchema.inMemoryStore(targetName);
    target = new JdbcTemplate(targetDb);
    connections.insert(
        new SqlJobConnection(
            "conn-1",
            "Target H2",
            null,
            SqlDialect.GENERIC,
            "jdbc:h2:mem:" + targetName + ";DB_CLOSE_DELAY=-1",
            null,
            "sa",
            "",
            null,
            Instant.now(),
            Instant.now()));
  }

  @AfterEach
  void tearDown() {
    pools.destroy();
  }

  private SqlJobExecutor executor(DbSchedulerUiSqlProperties properties) {
    return new SqlJobExecutor(connections, executions, pools, properties);
  }

  private static SqlJobData job(String script, int timeoutSeconds) {
    return new SqlJobData(
        "job-1", "Test job", null, "conn-1", script, null, timeoutSeconds, null, null);
  }

  private static SqlJobData job(List<String> scripts, int timeoutSeconds) {
    return new SqlJobData(
        "job-1", "Test job", null, "conn-1", null, scripts, timeoutSeconds, null, null);
  }

  @Test
  void executesScriptAndRecordsSuccess() {
    SqlJobExecution result =
        executor(new DbSchedulerUiSqlProperties("", 300, 10_000_000L))
            .execute(
                job(
                    "CREATE TABLE t(x INT); INSERT INTO t VALUES (1); INSERT INTO t VALUES (2); SELECT * FROM t",
                    300),
                "job-1",
                TriggerType.SCHEDULE);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.statementsExecuted()).isEqualTo(4);
    // 1 + 2 update counts + 2 result rows
    assertThat(result.rowsAffected()).isEqualTo(4);
    assertThat(result.rowsCapped()).isFalse();
    assertThat(target.queryForObject("select count(*) from t", Integer.class)).isEqualTo(2);

    SqlJobExecution stored = executions.findRecentByJob("job-1", 10).get(0);
    assertThat(stored.succeeded()).isTrue();
    assertThat(stored.triggeredBy()).isEqualTo(TriggerType.SCHEDULE);
    assertThat(stored.scriptText()).contains("CREATE TABLE t");
  }

  @Test
  void failureIsRecordedAndRethrown() {
    SqlJobExecutor executor = executor(new DbSchedulerUiSqlProperties("", 300, 10_000_000L));
    SqlJobData data = job("CREATE TABLE ok(x INT); SELECT * FROM does_not_exist", 300);

    assertThatExceptionOfType(SqlJobExecutionException.class)
        .isThrownBy(() -> executor.execute(data, "job-1", TriggerType.SCHEDULE));

    SqlJobExecution stored = executions.findRecentByJob("job-1", 10).get(0);
    assertThat(stored.succeeded()).isFalse();
    assertThat(stored.statementsExecuted()).isEqualTo(1);
    assertThat(stored.errorClass()).isNotBlank();
    assertThat(stored.errorMessage()).containsIgnoringCase("does_not_exist");
    // first statement committed despite later failure (autoCommit)
    assertThat(target.queryForObject("select count(*) from ok", Integer.class)).isZero();
  }

  @Test
  void executesScriptsInOrderAndRecordsThemTogether() {
    SqlJobExecution result =
        executor(new DbSchedulerUiSqlProperties("", 300, 10_000_000L))
            .execute(
                job(
                    List.of(
                        "CREATE TABLE ord(x INT); INSERT INTO ord VALUES (1);",
                        "INSERT INTO ord VALUES (2)"),
                    300),
                "job-1",
                TriggerType.SCHEDULE);

    assertThat(result.succeeded()).isTrue();
    assertThat(result.statementsExecuted()).isEqualTo(3);
    assertThat(target.queryForObject("select count(*) from ord", Integer.class)).isEqualTo(2);

    SqlJobExecution stored = executions.findRecentByJob("job-1", 10).get(0);
    assertThat(stored.scriptText())
        .contains("CREATE TABLE ord")
        .contains("INSERT INTO ord VALUES (2)");
  }

  @Test
  void failureInSecondScriptStopsAndNamesTheScript() {
    SqlJobExecutor executor = executor(new DbSchedulerUiSqlProperties("", 300, 10_000_000L));
    SqlJobData data = job(List.of("CREATE TABLE s1(x INT)", "SELECT * FROM missing_table"), 300);

    assertThatExceptionOfType(SqlJobExecutionException.class)
        .isThrownBy(() -> executor.execute(data, "job-1", TriggerType.SCHEDULE))
        .withMessageContaining("script 2 of 2");

    SqlJobExecution stored = executions.findRecentByJob("job-1", 10).get(0);
    assertThat(stored.succeeded()).isFalse();
    assertThat(stored.statementsExecuted()).isEqualTo(1);
    // first script's effect survived (autoCommit, fail-fast stops only what follows)
    assertThat(target.queryForObject("select count(*) from s1", Integer.class)).isZero();
  }

  @Test
  void unknownConnectionFails() {
    SqlJobExecutor executor = executor(new DbSchedulerUiSqlProperties("", 300, 10_000_000L));
    SqlJobData data =
        new SqlJobData("job-x", "x", null, "missing", "SELECT 1", null, 300, null, null);
    assertThatExceptionOfType(SqlJobExecutionException.class)
        .isThrownBy(() -> executor.execute(data, "job-x", TriggerType.SCHEDULE))
        .withMessageContaining("unknown connection");
  }

  @Test
  void capsRowsCountedForLargeResultSets() {
    SqlJobExecution result =
        executor(new DbSchedulerUiSqlProperties("", 300, 2L))
            .execute(
                job("SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4", 300),
                "job-1",
                TriggerType.MANUAL);
    assertThat(result.rowsCapped()).isTrue();
    assertThat(result.succeeded()).isTrue();
  }

  @Test
  void queryTimeoutCancelsLongRunningStatement() {
    SqlJobExecutor executor = executor(new DbSchedulerUiSqlProperties("", 300, 10_000_000L));
    String slow =
        "WITH recursive r(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM r WHERE n < 100000000)"
            + " SELECT count(*) FROM r";
    long start = System.currentTimeMillis();
    assertThatExceptionOfType(SqlJobExecutionException.class)
        .isThrownBy(() -> executor.execute(job(slow, 1), "job-1", TriggerType.SCHEDULE));
    // should be cancelled at ~1s (query timeout), far before the recursion would finish
    assertThat(System.currentTimeMillis() - start).isLessThan(10_000);
    assertThat(executions.findRecentByJob("job-1", 1).get(0).succeeded()).isFalse();
  }
}
