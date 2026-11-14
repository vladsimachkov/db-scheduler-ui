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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import no.bekk.dbscheduler.ui.sql.autoconfigure.DbSchedulerUiSqlAutoConfiguration;
import no.bekk.dbscheduler.ui.sql.dto.SqlJobRequest;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.repo.SqlJobExecutionRepository;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.model.SqlSchema;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Schema-grant enforcement: hidden jobs answer 404, cross-schema writes answer 400. */
class SqlJobServiceAccessTest {

  @SuppressWarnings("unchecked")
  private final SchedulerClient client = mock(SchedulerClient.class);

  private final SqlConnectionRepository connections = mock(SqlConnectionRepository.class);
  private final SqlJobExecutionRepository executions = mock(SqlJobExecutionRepository.class);
  private final no.bekk.dbscheduler.ui.users.repo.SchemaRepository schemas =
      mock(no.bekk.dbscheduler.ui.users.repo.SchemaRepository.class);
  private final RecurringTaskWithPersistentSchedule<SqlJobData> recurringTask =
      Tasks.recurringWithPersistentSchedule(DbSchedulerUiSqlAutoConfiguration.SQL_SCRIPT_TASK)
          .execute((inst, ctx) -> {});
  private final OneTimeTask<SqlJobData> onceTask =
      Tasks.oneTime(DbSchedulerUiSqlAutoConfiguration.SQL_SCRIPT_ONCE_TASK)
          .execute((inst, ctx) -> {});

  /**
   * Mutable so a successful create becomes visible to the follow-up getJob, as with a real store.
   */
  private final List<ScheduledExecution<SqlJobData>> recurring = new ArrayList<>();

  private void stubStore() {
    when(client.getScheduledExecutionsForTask(
            eq(SqlJobService.SQL_SCRIPT_TASK),
            eq(SqlJobData.class),
            any(ScheduledExecutionsFilter.class)))
        .thenAnswer(inv -> List.copyOf(recurring));
    when(client.getScheduledExecutionsForTask(
            eq(SqlJobService.SQL_SCRIPT_ONCE_TASK),
            eq(SqlJobData.class),
            any(ScheduledExecutionsFilter.class)))
        .thenReturn(List.of());
  }

  private SqlJobService service(SqlAccessGuard guard) {
    return new SqlJobService(client, recurringTask, onceTask, connections, executions, guard);
  }

  private static SqlAccessGuard guard(boolean admin, String... granted) {
    Set<String> grantedSchemas = Set.of(granted);
    return new SqlAccessGuard() {
      @Override
      public AuthPrincipal current() {
        return new AuthPrincipal("u", "u", admin);
      }

      @Override
      public boolean isAdmin() {
        return admin;
      }

      @Override
      public boolean canAccess(String schemaId) {
        return admin || (schemaId != null && grantedSchemas.contains(schemaId));
      }

      @Override
      public void require(String schemaId) {
        if (!canAccess(schemaId)) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
      }

      @Override
      public Set<String> accessibleSchemaIds() {
        return admin ? null : grantedSchemas;
      }
    };
  }

  private void givenJob(String jobId, String schemaId, String connectionId) {
    SqlJobData data =
        new SqlJobData(
            jobId,
            "job " + jobId,
            "d",
            connectionId,
            null,
            List.of("SELECT 1;"),
            60,
            "0 0 * * * *",
            null,
            schemaId);
    recurring.add(
        executionOf(new TaskInstance<>(SqlJobService.SQL_SCRIPT_TASK, jobId, data), data));
  }

  @SuppressWarnings("unchecked")
  private static ScheduledExecution<SqlJobData> executionOf(
      TaskInstance<SqlJobData> taskInstance, SqlJobData data) {
    ScheduledExecution<SqlJobData> execution = mock(ScheduledExecution.class);
    when(execution.getTaskInstance()).thenReturn(taskInstance);
    when(execution.getData()).thenReturn(data);
    when(execution.getExecutionTime()).thenReturn(Instant.now());
    return execution;
  }

  private static SqlJobConnection connection(String id, String schemaId) {
    Instant now = Instant.now();
    return new SqlJobConnection(
        id,
        "c-" + id,
        schemaId,
        SqlDialect.GENERIC,
        "jdbc:h2:mem:x",
        null,
        null,
        null,
        null,
        now,
        now);
  }

  private static SqlJobRequest request(String schemaId, String connectionId) {
    return new SqlJobRequest(
        "job", "d", schemaId, connectionId, List.of("SELECT 1;"), 60, "0 0 * * * *", null);
  }

  @Test
  void listJobsHidesSchemasTheUserWasNotGranted() {
    stubStore();
    givenJob("job-1", "s1", "conn-1");
    givenJob("job-2", "s2", "conn-1");
    givenJob("job-3", null, "conn-1"); // legacy unassigned: admin-only

    List<String> visible =
        service(guard(false, "s1")).listJobs().stream().map(job -> job.jobId()).toList();

    assertThat(visible).containsExactly("job-1");
  }

  @Test
  void listJobsShowsEverythingToAdmin() {
    stubStore();
    givenJob("job-1", "s1", "conn-1");
    givenJob("job-2", "s2", "conn-1");
    givenJob("job-3", null, "conn-1");

    assertThat(service(guard(true)).listJobs()).hasSize(3);
  }

  @Test
  void withoutGuardEverythingIsVisibleAsBefore() {
    stubStore();
    givenJob("job-1", "s1", "conn-1");
    givenJob("job-2", null, "conn-1");

    assertThat(service(null).listJobs()).hasSize(2);
  }

  @Test
  void getJobInHiddenSchemaAnswers404() {
    stubStore();
    givenJob("job-2", "s2", "conn-1");

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).getJob("job-2"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
  }

  @Test
  void runNowOnHiddenJobAnswers404AndTouchesNothing() {
    stubStore();
    givenJob("job-2", "s2", "conn-1");

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).runNow("job-2"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(client, never()).reschedule(any(TaskInstance.class), any(Instant.class));
  }

  @Test
  void deleteOnHiddenJobAnswers404AndDoesNotCancel() {
    stubStore();
    givenJob("job-2", "s2", "conn-1");

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).delete("job-2"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(client, never()).cancel(any(TaskInstance.class));
  }

  @Test
  void executionsForHiddenJobAnswers404() {
    stubStore();
    givenJob("job-2", "s2", "conn-1");

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).executionsFor("job-2", 10))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
  }

  @Test
  void executionsForUnscheduledJobStillReturnsHistory() {
    // one-time jobs are deleted from the store on completion; their history must stay readable
    stubStore();
    when(executions.findRecentByJob(eq("gone"), anyInt())).thenReturn(List.of());

    assertThat(service(guard(false, "s1")).executionsFor("gone", 10)).isEmpty();
    assertThat(service(null).executionsFor("gone", 10)).isEmpty();
  }

  @Test
  void updateHiddenJobAnswers404AndTouchesNothing() {
    stubStore();
    givenJob("job-2", "s2", "conn-1");
    when(connections.findById("conn-1")).thenReturn(Optional.of(connection("conn-1", "s1")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).update("job-2", request("s1", "conn-1")))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(client, never()).reschedule(any(TaskInstance.class), any(Instant.class));
    verify(client, never()).reschedule(any(SchedulableInstance.class));
    verify(client, never()).cancel(any(TaskInstance.class));
  }

  @Test
  void updateMovingToUngrantedSchemaAnswers404() {
    stubStore();
    givenJob("job-1", "s1", "conn-1");
    when(connections.findById("conn-1")).thenReturn(Optional.of(connection("conn-1", "s1")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).update("job-1", request("s2", "conn-1")))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(client, never()).reschedule(any(SchedulableInstance.class));
  }

  @Test
  void updateWithCrossSchemaConnectionIsRejectedEvenForAdmin() {
    stubStore();
    givenJob("job-1", "s1", "conn-1");
    when(connections.findById("conn-2")).thenReturn(Optional.of(connection("conn-2", "s2")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(true)).update("job-1", request("s1", "conn-2")))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST)
        .withMessageContaining("belong");
    verify(client, never()).reschedule(any(SchedulableInstance.class));
  }

  @Test
  void updateWithinOwnSchemaSucceeds() {
    stubStore();
    givenJob("job-1", "s1", "conn-1");
    when(connections.findById("conn-1")).thenReturn(Optional.of(connection("conn-1", "s1")));
    when(connections.findAll()).thenReturn(List.of(connection("conn-1", "s1")));

    var updated = service(guard(false, "s1")).update("job-1", request("s1", "conn-1"));

    assertThat(updated.schemaId()).isEqualTo("s1");
    verify(client).reschedule(any(SchedulableInstance.class));
  }

  @Test
  void createWithoutSchemaIsRejectedForNonAdmin() {
    stubStore();
    when(connections.findById("conn-1")).thenReturn(Optional.of(connection("conn-1", "s1")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).create(request(null, "conn-1")))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST)
        .withMessageContaining("A schema is required");
    verify(client, never()).scheduleIfNotExists(any(SchedulableInstance.class));
  }

  @Test
  void createWithConnectionFromHiddenSchemaLooksLikeUnknownConnection() {
    stubStore();
    when(connections.findById("conn-2")).thenReturn(Optional.of(connection("conn-2", "s2")));

    // same answer as a nonexistent connection — the hidden schema is not confirmed to exist
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).create(request("s1", "conn-2")))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST)
        .withMessageContaining("Unknown connection");
  }

  @Test
  void createWithCrossSchemaConnectionIsRejectedEvenForAdmin() {
    stubStore();
    when(connections.findById("conn-2")).thenReturn(Optional.of(connection("conn-2", "s2")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(true)).create(request("s1", "conn-2")))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST)
        .withMessageContaining("belong");
  }

  @Test
  void schemaNameComesOnlyFromVisibleSchemasAndHiddenConnectionNameDoesNotLeak() {
    stubStore();
    givenJob("job-1", "s1", "conn-hidden"); // legacy job: own schema granted, connection hidden
    when(connections.findAll()).thenReturn(List.of(connection("conn-hidden", "s2")));
    Instant now = Instant.now();
    when(schemas.findAll())
        .thenReturn(
            List.of(
                new SqlSchema("s1", "One", null, now, now),
                new SqlSchema("s2", "Two", null, now, now)));

    var jobs =
        new SqlJobService(
                client,
                recurringTask,
                onceTask,
                connections,
                executions,
                guard(false, "s1"),
                schemas)
            .listJobs();

    assertThat(jobs).hasSize(1);
    assertThat(jobs.get(0).schemaName()).isEqualTo("One");
    assertThat(jobs.get(0).connectionName()).isNull();
  }

  @Test
  void anyJobInSchemaChecksBothTaskTypes() {
    stubStore();
    givenJob("job-1", "s1", "conn-1");

    SqlJobService svc = service(guard(true));
    assertThat(svc.anyJobInSchema("s1")).isTrue();
    assertThat(svc.anyJobInSchema("s2")).isFalse();
  }

  @Test
  @SuppressWarnings("unchecked")
  void createInGrantedSchemaWithSameSchemaConnectionSucceeds() {
    stubStore();
    when(connections.findById("conn-1")).thenReturn(Optional.of(connection("conn-1", "s1")));
    when(connections.findAll()).thenReturn(List.of(connection("conn-1", "s1")));
    when(client.scheduleIfNotExists(any(SchedulableInstance.class)))
        .thenAnswer(
            inv -> {
              SchedulableInstance<SqlJobData> inst = inv.getArgument(0);
              TaskInstance<SqlJobData> ti = inst.getTaskInstance();
              recurring.add(executionOf(ti, ti.getData()));
              return true;
            });

    var created = service(guard(false, "s1")).create(request("s1", "conn-1"));

    assertThat(created.schemaId()).isEqualTo("s1");
    verify(client).scheduleIfNotExists(any(SchedulableInstance.class));
  }
}
