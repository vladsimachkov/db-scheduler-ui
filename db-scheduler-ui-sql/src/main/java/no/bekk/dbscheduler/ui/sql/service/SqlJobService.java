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

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.ScheduledExecutionsFilter;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.exceptions.TaskInstanceCurrentlyExecutingException;
import com.github.kagkarlsson.scheduler.exceptions.TaskInstanceNotFoundException;
import com.github.kagkarlsson.scheduler.task.ExecutionComplete;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTaskWithPersistentSchedule;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import no.bekk.dbscheduler.ui.sql.dto.SqlJobDto;
import no.bekk.dbscheduler.ui.sql.dto.SqlJobRequest;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.model.SqlJobExecution;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.repo.SqlJobExecutionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * CRUD for SQL jobs, mapped onto db-scheduler instances.
 *
 * <p>A job is a scheduled execution of one of two generic tasks: {@code sql-script-task}
 * (recurring, cron carried per-instance via {@code ScheduleAndData}) or {@code
 * sql-script-once-task} (one-time). Editing a job reschedules its instance with new data; there is
 * no separate definitions table.
 */
public class SqlJobService {

  public static final String SQL_SCRIPT_TASK = "sql-script-task";
  public static final String SQL_SCRIPT_ONCE_TASK = "sql-script-once-task";

  private final SchedulerClient client;
  private final RecurringTaskWithPersistentSchedule<SqlJobData> recurringTask;
  private final OneTimeTask<SqlJobData> onceTask;
  private final SqlConnectionRepository connections;
  private final SqlJobExecutionRepository executions;

  public SqlJobService(
      SchedulerClient client,
      RecurringTaskWithPersistentSchedule<SqlJobData> recurringTask,
      OneTimeTask<SqlJobData> onceTask,
      SqlConnectionRepository connections,
      SqlJobExecutionRepository executions) {
    this.client = client;
    this.recurringTask = recurringTask;
    this.onceTask = onceTask;
    this.connections = connections;
    this.executions = executions;
  }

  public List<SqlJobDto> listJobs() {
    Map<String, String> connectionNames =
        connections.findAll().stream()
            .collect(Collectors.toMap(SqlJobConnection::id, SqlJobConnection::name, (a, b) -> a));
    List<SqlJobDto> jobs = new ArrayList<>();
    for (ScheduledExecution<SqlJobData> execution : scheduled(SqlScriptKind.RECURRING)) {
      jobs.add(toDto(execution, true, connectionNames));
    }
    for (ScheduledExecution<SqlJobData> execution : scheduled(SqlScriptKind.ONCE)) {
      jobs.add(toDto(execution, false, connectionNames));
    }
    jobs.sort(
        Comparator.comparing(SqlJobDto::nextRunTime, Comparator.nullsLast(Instant::compareTo)));
    return jobs;
  }

  public SqlJobDto getJob(String jobId) {
    return find(jobId)
        .map(found -> toDto(found.execution(), found.recurring(), connectionNames()))
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
  }

  public SqlJobDto create(SqlJobRequest request) {
    validate(request);
    String jobId = UUID.randomUUID().toString();
    SqlJobData data = toData(jobId, request, request.schemaId());
    boolean created;
    if (data.isRecurring()) {
      created = client.scheduleIfNotExists(recurringTask.schedulableInstance(jobId, data));
    } else {
      Instant runAt = request.runAt() != null ? request.runAt() : Instant.now();
      created =
          client.scheduleIfNotExists(new TaskInstance<>(SQL_SCRIPT_ONCE_TASK, jobId, data), runAt);
    }
    if (!created) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Job already exists: " + jobId);
    }
    return getJob(jobId);
  }

  public SqlJobDto update(String jobId, SqlJobRequest request) {
    validate(request);
    Found existing =
        find(jobId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
    // a request that omits schemaId keeps the job's current assignment (no silent unassignment)
    String schemaId =
        request.schemaId() == null || request.schemaId().isBlank()
            ? existing.execution().getData().getSchemaId()
            : request.schemaId().trim();
    SqlJobData data = toData(jobId, request, schemaId);
    if (existing.recurring() && data.isRecurring()) {
      clientOp(() -> client.reschedule(recurringTask.schedulableInstance(jobId, data)));
    } else if (!existing.recurring() && !data.isRecurring()) {
      Instant runAt =
          request.runAt() != null ? request.runAt() : existing.execution().getExecutionTime();
      clientOp(
          () -> client.reschedule(new TaskInstance<>(SQL_SCRIPT_ONCE_TASK, jobId), runAt, data));
    } else {
      // Task type switched: cancel the old instance and create a new one under the other task
      clientOp(() -> client.cancel(existingTaskInstance(existing.recurring(), jobId)));
      if (data.isRecurring()) {
        client.scheduleIfNotExists(recurringTask.schedulableInstance(jobId, data));
      } else {
        Instant runAt = request.runAt() != null ? request.runAt() : Instant.now();
        client.scheduleIfNotExists(new TaskInstance<>(SQL_SCRIPT_ONCE_TASK, jobId, data), runAt);
      }
    }
    return getJob(jobId);
  }

  /**
   * Reschedules the instance to now. Note: db-scheduler's reschedule resets {@code lastSuccess},
   * {@code lastFailure} and the failure-count on the instance — a known limitation of storing job
   * state in the scheduled execution.
   */
  public void runNow(String jobId) {
    Found existing =
        find(jobId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
    clientOp(
        () -> client.reschedule(existingTaskInstance(existing.recurring(), jobId), Instant.now()));
  }

  public void delete(String jobId) {
    Found existing =
        find(jobId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found: " + jobId));
    clientOp(() -> client.cancel(existingTaskInstance(existing.recurring(), jobId)));
  }

  public List<SqlJobExecution> executionsFor(String jobId, int limit) {
    return executions.findRecentByJob(jobId, Math.min(Math.max(1, limit), 500));
  }

  /**
   * Next {@code count} execution times for a cron pattern, computed by the same parser the
   * scheduler uses.
   */
  public List<Instant> previewSchedule(String cron, int count) {
    CronSchedule schedule;
    try {
      schedule = new CronSchedule(cron);
    } catch (RuntimeException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Invalid cron expression: " + e.getMessage(), e);
    }
    List<Instant> next = new ArrayList<>();
    Instant time = schedule.getInitialExecutionTime(Instant.now());
    for (int i = 0; i < count && time != null && !time.equals(CronSchedule.NEVER); i++) {
      next.add(time);
      time = schedule.getNextExecutionTime(ExecutionComplete.simulatedSuccess(time.plusMillis(1)));
    }
    return next;
  }

  private void validate(SqlJobRequest request) {
    if (request.isRecurring()) {
      try {
        new CronSchedule(request.cronExpression().trim());
      } catch (RuntimeException e) {
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Invalid cron expression: " + e.getMessage(), e);
      }
    }
    if (connections.findById(request.connectionId()).isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Unknown connection: " + request.connectionId());
    }
  }

  private static SqlJobData toData(String jobId, SqlJobRequest request, String schemaId) {
    return new SqlJobData(
        jobId,
        request.name(),
        request.description(),
        request.connectionId(),
        // the single-script field stays empty for new data: the ordered list is the source now
        null,
        request.scripts(),
        request.timeoutSeconds() != null ? request.timeoutSeconds() : 0,
        request.isRecurring() ? request.cronExpression().trim() : null,
        // jobs always run in the server's time zone; the data field is vestigial
        null,
        schemaId);
  }

  private List<ScheduledExecution<SqlJobData>> scheduled(SqlScriptKind kind) {
    // all(): a currently-running (picked) job must stay visible and manageable
    ScheduledExecutionsFilter includePicked = ScheduledExecutionsFilter.all();
    return kind == SqlScriptKind.RECURRING
        ? client.getScheduledExecutionsForTask(SQL_SCRIPT_TASK, SqlJobData.class, includePicked)
        : client.getScheduledExecutionsForTask(
            SQL_SCRIPT_ONCE_TASK, SqlJobData.class, includePicked);
  }

  /** Maps db-scheduler's instance-state exceptions onto HTTP status codes. */
  private static void clientOp(Runnable operation) {
    try {
      operation.run();
    } catch (TaskInstanceCurrentlyExecutingException e) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Job is currently executing, try again when it finishes", e);
    } catch (TaskInstanceNotFoundException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found", e);
    }
  }

  private Optional<Found> find(String jobId) {
    Optional<ScheduledExecution<SqlJobData>> recurring =
        scheduled(SqlScriptKind.RECURRING).stream()
            .filter(e -> e.getTaskInstance().getId().equals(jobId))
            .findFirst();
    if (recurring.isPresent()) {
      return Optional.of(new Found(recurring.get(), true));
    }
    return scheduled(SqlScriptKind.ONCE).stream()
        .filter(e -> e.getTaskInstance().getId().equals(jobId))
        .findFirst()
        .map(e -> new Found(e, false));
  }

  private static TaskInstance<SqlJobData> existingTaskInstance(boolean recurring, String jobId) {
    return new TaskInstance<>(recurring ? SQL_SCRIPT_TASK : SQL_SCRIPT_ONCE_TASK, jobId);
  }

  private Map<String, String> connectionNames() {
    return connections.findAll().stream()
        .collect(Collectors.toMap(SqlJobConnection::id, SqlJobConnection::name, (a, b) -> a));
  }

  private static SqlJobDto toDto(
      ScheduledExecution<SqlJobData> execution,
      boolean recurring,
      Map<String, String> connectionNames) {
    SqlJobData data = execution.getData();
    return new SqlJobDto(
        data.getJobId(),
        data.getName(),
        data.getDescription(),
        data.getSchemaId(),
        data.getConnectionId(),
        connectionNames.get(data.getConnectionId()),
        data.getScripts(),
        data.getTimeoutSeconds(),
        data.getCronExpression(),
        recurring,
        execution.getExecutionTime(),
        execution.getLastSuccess(),
        execution.getLastFailure(),
        execution.getConsecutiveFailures(),
        execution.isPicked());
  }

  private enum SqlScriptKind {
    RECURRING,
    ONCE
  }

  private record Found(ScheduledExecution<SqlJobData> execution, boolean recurring) {}
}
