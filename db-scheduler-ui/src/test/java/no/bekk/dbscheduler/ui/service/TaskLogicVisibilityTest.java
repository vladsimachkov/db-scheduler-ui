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
package no.bekk.dbscheduler.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.ScheduledExecutionsFilter;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Execution;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import no.bekk.dbscheduler.ui.model.GetTasksResponse;
import no.bekk.dbscheduler.ui.model.PollResponse;
import no.bekk.dbscheduler.ui.model.TaskDetailsRequestParams;
import no.bekk.dbscheduler.ui.model.TaskRequestParams;
import no.bekk.dbscheduler.ui.util.Caching;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class TaskLogicVisibilityTest {

  private static ScheduledExecution<Object> execution(
      String taskName, String instanceId, Object data) {
    return execution(taskName, instanceId, data, false);
  }

  private static ScheduledExecution<Object> execution(
      String taskName, String instanceId, Object data, boolean picked) {
    TaskInstance<Object> taskInstance = new TaskInstance<>(taskName, instanceId, data);
    Execution execution =
        new Execution(
            Instant.now(),
            taskInstance,
            picked,
            picked ? "worker-1" : null,
            null,
            null,
            0,
            null,
            0);
    return new ScheduledExecution<>(Object.class, execution);
  }

  private static Scheduler schedulerWith(ScheduledExecution<Object>... executions) {
    Scheduler scheduler = mock(Scheduler.class);
    // fresh mutable list per call: getExecutionsFromDBWithoutUpdatingCache addAll()s onto it
    when(scheduler.getScheduledExecutions())
        .thenAnswer(inv -> new ArrayList<>(List.of(executions)));
    when(scheduler.getScheduledExecutions(any(ScheduledExecutionsFilter.class)))
        .thenAnswer(inv -> new ArrayList<>());
    return scheduler;
  }

  private static TaskRequestParams params(String schemaId) {
    return new TaskRequestParams(
        null, null, null, null, null, null, null, null, null, null, null, null, schemaId);
  }

  /** Hides every execution whose task name is {@code hiddenTaskName}. */
  private static ExecutionVisibilityFilter hideByName(String hiddenTaskName) {
    return (taskName, taskInstance, taskData, schemaId) -> !hiddenTaskName.equals(taskName);
  }

  @Test
  void getAllTasks_hidesInvisibleExecutionsAndCountsAfterFilter() {
    Scheduler scheduler =
        schedulerWith(execution("visible-task", "i1", null), execution("hidden-task", "i2", null));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true, hideByName("hidden-task"));

    GetTasksResponse response = taskLogic.getAllTasks(params(null));

    assertThat(response.getNumberOfItems()).isEqualTo(1);
    assertThat(response.getItems()).extracting("taskName").containsExactly("visible-task");
  }

  @Test
  void getAllTasks_forwardsSchemaIdToFilter() {
    Scheduler scheduler = schedulerWith(execution("some-task", "i1", null));
    AtomicReference<String> seenSchemaId = new AtomicReference<>();
    TaskLogic taskLogic =
        new TaskLogic(
            scheduler,
            new Caching(),
            true,
            (taskName, taskInstance, taskData, schemaId) -> {
              seenSchemaId.set(schemaId);
              return true;
            });

    taskLogic.getAllTasks(params("schema-x"));

    assertThat(seenSchemaId.get()).isEqualTo("schema-x");
  }

  @Test
  void getAllTasks_withoutFilter_showsEverything() {
    Scheduler scheduler =
        schedulerWith(execution("a-task", "i1", null), execution("b-task", "i2", null));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true);

    GetTasksResponse response = taskLogic.getAllTasks(params(null));

    assertThat(response.getNumberOfItems()).isEqualTo(2);
  }

  @Test
  void getTask_hiddenExecutionIsNotFound() {
    Scheduler scheduler =
        schedulerWith(
            execution("hidden-task", "i2", "payload"), execution("visible-task", "i1", null));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true, hideByName("hidden-task"));

    assertThatThrownBy(
            () ->
                taskLogic.getTask(
                    TaskDetailsRequestParams.builder()
                        .taskName("hidden-task")
                        .taskId("i2")
                        .build()))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

    GetTasksResponse visible =
        taskLogic.getTask(
            TaskDetailsRequestParams.builder().taskName("visible-task").taskId("i1").build());
    assertThat(visible.getNumberOfItems()).isEqualTo(1);
  }

  @Test
  void pollTasks_excludesHiddenExecutionsFromCounters() {
    Scheduler scheduler =
        schedulerWith(execution("visible-task", "i1", null), execution("hidden-task", "i2", null));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true, hideByName("hidden-task"));

    PollResponse response = taskLogic.pollTasks(TaskDetailsRequestParams.builder().build());

    assertThat(response.getNewTasks()).isEqualTo(1);
  }

  @Test
  void runTaskNow_hiddenExecutionIsNotFound() {
    Scheduler scheduler = mock(Scheduler.class);
    when(scheduler.getScheduledExecution(any(TaskInstanceId.class)))
        .thenReturn(Optional.of(execution("hidden-task", "i1", null)));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true, hideByName("hidden-task"));

    assertThatThrownBy(() -> taskLogic.runTaskNow("i1", "hidden-task", null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(scheduler, never()).reschedule(any(), any());
  }

  @Test
  void runTaskNow_visibleExecutionIsRescheduled() {
    ScheduledExecution<Object> exec = execution("visible-task", "i1", null);
    Scheduler scheduler = mock(Scheduler.class);
    when(scheduler.getScheduledExecution(any(TaskInstanceId.class))).thenReturn(Optional.of(exec));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true, hideByName("hidden-task"));

    taskLogic.runTaskNow("i1", "visible-task", null);

    verify(scheduler, times(1)).reschedule(any(), any());
  }

  @Test
  void runTaskNow_hiddenAndPicked_throwsNotFound() {
    Scheduler scheduler = mock(Scheduler.class);
    when(scheduler.getScheduledExecution(any(TaskInstanceId.class)))
        .thenReturn(Optional.of(execution("hidden-task", "i1", null, true)));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true, hideByName("hidden-task"));

    assertThatThrownBy(() -> taskLogic.runTaskNow("i1", "hidden-task", null))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(scheduler, never()).reschedule(any(), any());
  }

  @Test
  void getTask_partiallyHiddenGroup_countsAfterFilter() {
    Scheduler scheduler =
        schedulerWith(
            execution("sql-script-task", "visible-id", "a"),
            execution("sql-script-task", "hidden-id", "b"));
    TaskLogic taskLogic =
        new TaskLogic(
            scheduler,
            new Caching(),
            true,
            (taskName, taskInstance, taskData, schemaId) -> !"hidden-id".equals(taskInstance));

    GetTasksResponse response =
        taskLogic.getTask(TaskDetailsRequestParams.builder().taskName("sql-script-task").build());

    assertThat(response.getNumberOfItems()).isEqualTo(1);
    assertThat(response.getItems().get(0).getTaskInstance()).containsExactly("visible-id");
  }

  @Test
  void deleteTask_hiddenExecutionIsNotFound() {
    Scheduler scheduler = mock(Scheduler.class);
    when(scheduler.getScheduledExecution(any(TaskInstanceId.class)))
        .thenReturn(Optional.of(execution("hidden-task", "i1", null)));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true, hideByName("hidden-task"));

    assertThatThrownBy(() -> taskLogic.deleteTask("i1", "hidden-task"))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    verify(scheduler, never()).cancel(any());
  }

  @Test
  void runTaskGroupNow_skipsHiddenExecutions() {
    ScheduledExecution<Object> visible = execution("group-task", "ok", null);
    Scheduler scheduler = mock(Scheduler.class);
    when(scheduler.getScheduledExecutions())
        .thenReturn(new ArrayList<>(List.of(visible, execution("group-task", "hidden-id", null))));
    when(scheduler.getScheduledExecutions(any(ScheduledExecutionsFilter.class)))
        .thenReturn(new ArrayList<>());
    when(scheduler.getScheduledExecution(any(TaskInstanceId.class)))
        .thenReturn(Optional.of(visible));
    TaskLogic taskLogic =
        new TaskLogic(
            scheduler,
            new Caching(),
            true,
            (taskName, taskInstance, taskData, schemaId) -> !"hidden-id".equals(taskInstance));

    taskLogic.runTaskGroupNow("group-task", false);

    verify(scheduler, times(1)).reschedule(any(), any());
  }
}
