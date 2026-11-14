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

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.sql.DataSource;
import no.bekk.dbscheduler.ui.sql.SqlTestSchema;
import no.bekk.dbscheduler.ui.sql.exec.IdGenerator;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.model.SqlJobExecution;
import no.bekk.dbscheduler.ui.sql.model.TriggerType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RepositoriesTest {

  private SqlConnectionRepository connections;
  private SqlJobExecutionRepository executions;

  @BeforeEach
  void setUp() {
    DataSource store = SqlTestSchema.inMemoryStore("repos_" + System.nanoTime());
    SqlTestSchema.applyH2Ddl(store);
    JdbcTemplate jdbc = new JdbcTemplate(store);
    connections = new SqlConnectionRepository(jdbc);
    executions = new SqlJobExecutionRepository(jdbc);
  }

  @Test
  void connectionRoundTrip() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    connections.insert(
        new SqlJobConnection(
            "c1",
            "Warehouse",
            SqlDialect.TRINO,
            "jdbc:trino://host:443/analytics",
            "io.trino.jdbc.TrinoDriver",
            "user",
            "enc:v1:xyz",
            "{\"key\":\"value\"}",
            now,
            now));

    SqlJobConnection loaded = connections.findById("c1").orElseThrow();
    assertThat(loaded.name()).isEqualTo("Warehouse");
    assertThat(loaded.dialect()).isEqualTo(SqlDialect.TRINO);
    assertThat(loaded.password()).isEqualTo("enc:v1:xyz");
    assertThat(loaded.propertiesJson()).isEqualTo("{\"key\":\"value\"}");
    assertThat(loaded.createdTime()).isEqualTo(now);

    connections.update(
        new SqlJobConnection(
            "c1",
            "Renamed",
            SqlDialect.ORACLE,
            "jdbc:oracle:thin:@h:1521:s",
            null,
            "u2",
            "pw",
            null,
            now,
            Instant.now()));
    assertThat(connections.findById("c1").orElseThrow().name()).isEqualTo("Renamed");
    assertThat(connections.findById("c1").orElseThrow().driverClass()).isNull();

    assertThat(connections.findAll()).hasSize(1);
  }

  @Test
  void addedDialectsRoundTrip() {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    for (SqlDialect dialect :
        List.of(
            SqlDialect.POSTGRESQL, SqlDialect.MARIADB, SqlDialect.MSSQL, SqlDialect.CLICKHOUSE)) {
      String id = "c-" + dialect.name();
      connections.insert(
          new SqlJobConnection(
              id, "n", dialect, "jdbc:example://h/db", null, null, null, null, now, now));
      assertThat(connections.findById(id).orElseThrow().dialect()).isEqualTo(dialect);
    }
    connections.deleteById("c1");
    assertThat(connections.findById("c1")).isEmpty();
  }

  @Test
  void executionHistoryIsNewestFirstAndCapped() {
    Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
    for (int i = 0; i < 5; i++) {
      executions.insert(execution("job-1", base.plus(i, ChronoUnit.MINUTES)));
    }
    executions.insert(execution("job-2", base));

    List<SqlJobExecution> recent = executions.findRecentByJob("job-1", 3);
    assertThat(recent).hasSize(3);
    assertThat(recent).allSatisfy(e -> assertThat(e.jobId()).isEqualTo("job-1"));
    assertThat(recent.get(0).timeStarted()).isAfterOrEqualTo(recent.get(1).timeStarted());
    assertThat(recent.get(0).timeStarted()).isAfterOrEqualTo(recent.get(2).timeStarted());
  }

  private static SqlJobExecution execution(String jobId, Instant time) {
    return new SqlJobExecution(
        IdGenerator.next(),
        jobId,
        "instance-1",
        "c1",
        SqlDialect.GENERIC,
        "SELECT 1",
        time,
        time.plusSeconds(1),
        true,
        1,
        10,
        false,
        1000,
        null,
        null,
        TriggerType.SCHEDULE);
  }
}
