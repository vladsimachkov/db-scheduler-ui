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
package no.bekk.dbscheduler.ui.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.sql.service.SqlJobService;
import org.junit.jupiter.api.Test;

/** Visibility rules for sql-job executions in the core task and log APIs. */
class SqlJobExecutionVisibilityTest {

  private final SqlAccessGuard guard = mock(SqlAccessGuard.class);
  private final SqlJobExecutionVisibility visibility = new SqlJobExecutionVisibility(guard);

  private static SqlJobData jobData(String schemaId) {
    return new SqlJobData(
        "job-1",
        "nightly",
        null,
        "conn-1",
        null,
        List.of("SELECT 1"),
        60,
        "0 0 * * * *",
        null,
        schemaId);
  }

  private static String sqlTaskName() {
    return SqlJobService.SQL_SCRIPT_TASK;
  }

  @Test
  void plainHostTask_visibleWithoutSchemaFilter() {
    assertThat(visibility.visible("some-host-task", "i1", "data", null)).isTrue();
  }

  @Test
  void plainHostTask_hiddenWhenSchemaFilterActive() {
    assertThat(visibility.visible("some-host-task", "i1", "data", "schema-a")).isFalse();
  }

  @Test
  void blankSchemaId_treatedAsNoFilter() {
    assertThat(visibility.visible("some-host-task", "i1", "data", "  ")).isTrue();
    assertThat(visibility.visible("some-host-task", "i1", "data", "")).isTrue();
  }

  @Test
  void grantedSchema_visible() {
    when(guard.canAccess("schema-a")).thenReturn(true);
    assertThat(visibility.visible(sqlTaskName(), "job-1", jobData("schema-a"), null)).isTrue();
  }

  @Test
  void ungrantedSchema_hidden() {
    when(guard.canAccess(any())).thenReturn(false);
    assertThat(visibility.visible(sqlTaskName(), "job-1", jobData("schema-b"), null)).isFalse();
  }

  @Test
  void onceTaskName_treatedLikeRecurring() {
    when(guard.canAccess("schema-a")).thenReturn(true);
    assertThat(
            visibility.visible(
                SqlJobService.SQL_SCRIPT_ONCE_TASK, "job-1", jobData("schema-a"), null))
        .isTrue();
  }

  @Test
  void legacyJobWithoutSchema_adminOnly() {
    when(guard.canAccess(null)).thenReturn(false);
    when(guard.isAdmin()).thenReturn(false);
    assertThat(visibility.visible(sqlTaskName(), "job-1", jobData(null), null)).isFalse();

    when(guard.canAccess(null)).thenReturn(true);
    assertThat(visibility.visible(sqlTaskName(), "job-1", jobData(null), null)).isTrue();
  }

  @Test
  void schemaFilter_narrowsToEquality() {
    when(guard.canAccess("schema-a")).thenReturn(true);
    assertThat(visibility.visible(sqlTaskName(), "job-1", jobData("schema-a"), "schema-a"))
        .isTrue();
    assertThat(visibility.visible(sqlTaskName(), "job-1", jobData("schema-a"), "schema-b"))
        .isFalse();
  }

  @Test
  void missingOrCorruptBlob_failClosedAdminOnly() {
    when(guard.isAdmin()).thenReturn(false);
    assertThat(visibility.visible(sqlTaskName(), "job-1", null, null)).isFalse();
    assertThat(visibility.visible(sqlTaskName(), "job-1", "not-a-job-data", null)).isFalse();

    when(guard.isAdmin()).thenReturn(true);
    assertThat(visibility.visible(sqlTaskName(), "job-1", null, null)).isTrue();
    // even for the admin a schema filter hides rows whose schema cannot be determined
    assertThat(visibility.visible(sqlTaskName(), "job-1", null, "schema-a")).isFalse();
  }

  @Test
  void needsTaskData_onlyForSqlJobTaskNames() {
    assertThat(visibility.needsTaskData(SqlJobService.SQL_SCRIPT_TASK)).isTrue();
    assertThat(visibility.needsTaskData(SqlJobService.SQL_SCRIPT_ONCE_TASK)).isTrue();
    assertThat(visibility.needsTaskData("some-host-task")).isFalse();
  }
}
