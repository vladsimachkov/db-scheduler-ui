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
package no.bekk.dbscheduler.ui.sql.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import no.bekk.dbscheduler.ui.model.TaskDisplay;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import org.junit.jupiter.api.Test;

class SqlJobTaskDisplayResolverTest {

  private final SqlJobTaskDisplayResolver resolver = new SqlJobTaskDisplayResolver();

  private static SqlJobData jobData(String name, String schemaId) {
    return new SqlJobData(
        "job-1", name, "desc", "conn-1", "SELECT 1;", List.of(), 30, "0 0 * * *", null, schemaId);
  }

  @Test
  void resolve_sqlJobData_carriesNameAndSchema() {
    TaskDisplay display =
        resolver.resolve("sql-script-task", "instance-1", jobData("nightly", "schema-1"));

    assertThat(display).isNotNull();
    assertThat(display.displayName()).isEqualTo("nightly");
    assertThat(display.schemaId()).isEqualTo("schema-1");
  }

  @Test
  void resolve_legacyBlobWithoutSchema_schemaIdIsNull() {
    TaskDisplay display =
        resolver.resolve("sql-script-task", "instance-1", jobData("legacy", null));

    assertThat(display).isNotNull();
    assertThat(display.displayName()).isEqualTo("legacy");
    assertThat(display.schemaId()).isNull();
  }

  @Test
  void resolve_foreignTaskData_returnsNull() {
    assertThat(resolver.resolve("some-task", "instance-1", "not-sql-data")).isNull();
  }

  @Test
  void resolve_nullTaskData_returnsNull() {
    assertThat(resolver.resolve("sql-script-task", "instance-1", null)).isNull();
  }
}
