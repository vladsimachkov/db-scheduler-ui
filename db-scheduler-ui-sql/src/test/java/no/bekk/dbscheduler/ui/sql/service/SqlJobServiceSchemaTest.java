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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.ScheduledExecutionsFilter;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.SchedulableInstance;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTaskWithPersistentSchedule;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import no.bekk.dbscheduler.ui.sql.dto.SqlJobRequest;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.repo.SqlJobExecutionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Update must never silently drop a job's schema assignment when the request omits schemaId. */
class SqlJobServiceSchemaTest {

  @SuppressWarnings("unchecked")
  private final SchedulerClient client = mock(SchedulerClient.class);

  private final SqlConnectionRepository connections = mock(SqlConnectionRepository.class);
  private final SqlJobExecutionRepository executions = mock(SqlJobExecutionRepository.class);
  private final RecurringTaskWithPersistentSchedule<SqlJobData> recurringTask =
      Tasks.recurringWithPersistentSchedule(
              no.bekk.dbscheduler.ui.sql.autoconfigure.DbSchedulerUiSqlAutoConfiguration
                  .SQL_SCRIPT_TASK)
          .execute((inst, ctx) -> {});
  private final OneTimeTask<SqlJobData> onceTask =
      Tasks.oneTime(
              no.bekk.dbscheduler.ui.sql.autoconfigure.DbSchedulerUiSqlAutoConfiguration
                  .SQL_SCRIPT_ONCE_TASK)
          .execute((inst, ctx) -> {});

  private final SqlJobService service =
      new SqlJobService(client, recurringTask, onceTask, connections, executions);

  private static SqlJobConnection connection() {
    Instant now = Instant.now();
    return new SqlJobConnection(
        "conn-1", "c", "s1", SqlDialect.GENERIC, "jdbc:h2:mem:x", null, null, null, null, now, now);
  }

  @SuppressWarnings("unchecked")
  private void givenRecurringJobWithSchema(String schemaId) {
    SqlJobData data =
        new SqlJobData(
            "job-1",
            "job",
            "d",
            "conn-1",
            null,
            List.of("SELECT 1;"),
            60,
            "0 0 * * * *",
            null,
            schemaId);
    ScheduledExecution<SqlJobData> execution = mock(ScheduledExecution.class);
    when(execution.getTaskInstance())
        .thenReturn(new TaskInstance<>(SqlJobService.SQL_SCRIPT_TASK, "job-1", data));
    when(execution.getData()).thenReturn(data);
    when(execution.getExecutionTime()).thenReturn(Instant.now());
    when(client.getScheduledExecutionsForTask(
            eq(SqlJobService.SQL_SCRIPT_TASK),
            eq(SqlJobData.class),
            any(ScheduledExecutionsFilter.class)))
        .thenReturn(List.of(execution));
    when(client.getScheduledExecutionsForTask(
            eq(SqlJobService.SQL_SCRIPT_ONCE_TASK),
            eq(SqlJobData.class),
            any(ScheduledExecutionsFilter.class)))
        .thenReturn(List.of());
    when(connections.findById("conn-1")).thenReturn(Optional.of(connection()));
  }

  private SqlJobData rescheduledData() {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<SchedulableInstance<SqlJobData>> captor =
        ArgumentCaptor.forClass(SchedulableInstance.class);
    verify(client).reschedule(captor.capture());
    return captor.getValue().getTaskInstance().getData();
  }

  @Test
  void updateWithoutSchemaIdKeepsExistingAssignment() {
    givenRecurringJobWithSchema("s1");
    service.update(
        "job-1",
        new SqlJobRequest(
            "renamed", "d", null, "conn-1", List.of("SELECT 2;"), 60, "0 0 * * * *", null));
    assertThat(rescheduledData().getSchemaId()).isEqualTo("s1");
  }

  @Test
  void updateWithExplicitSchemaIdMovesTheJob() {
    givenRecurringJobWithSchema("s1");
    service.update(
        "job-1",
        new SqlJobRequest(
            "renamed", "d", "s2", "conn-1", List.of("SELECT 2;"), 60, "0 0 * * * *", null));
    assertThat(rescheduledData().getSchemaId()).isEqualTo("s2");
  }
}
