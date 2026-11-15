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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.ScheduledExecutionsFilter;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Execution;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import no.bekk.dbscheduler.ui.model.GetTasksResponse;
import no.bekk.dbscheduler.ui.model.TaskDetailsRequestParams;
import no.bekk.dbscheduler.ui.model.TaskDisplay;
import no.bekk.dbscheduler.ui.model.TaskModel;
import no.bekk.dbscheduler.ui.model.TaskRequestParams;
import no.bekk.dbscheduler.ui.util.Caching;
import org.junit.jupiter.api.Test;

class TaskLogicDisplayTest {

  private static ScheduledExecution<Object> execution(
      String taskName, String instanceId, Object data) {
    TaskInstance<Object> taskInstance = new TaskInstance<>(taskName, instanceId, data);
    Execution execution =
        new Execution(Instant.now(), taskInstance, false, null, null, null, 0, null, 0);
    return new ScheduledExecution<>(Object.class, execution);
  }

  private static Scheduler schedulerWith(ScheduledExecution<Object>... executions) {
    Scheduler scheduler = mock(Scheduler.class);
    when(scheduler.getScheduledExecutions())
        .thenAnswer(inv -> new ArrayList<>(List.of(executions)));
    when(scheduler.getScheduledExecutions(any(ScheduledExecutionsFilter.class)))
        .thenAnswer(inv -> new ArrayList<>());
    return scheduler;
  }

  private static TaskRequestParams params() {
    return new TaskRequestParams(
        null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  /** Resolves a display for every {@code sql-script-task} execution from its String payload. */
  private static TaskDisplayResolver sqlJobLikeResolver() {
    return (taskName, taskInstance, taskData) ->
        "sql-script-task".equals(taskName) ? new TaskDisplay("job " + taskData, "schema-1") : null;
  }

  @Test
  void getAllTasks_resolvesDisplaysForUngroupedExecutions() {
    Scheduler scheduler =
        schedulerWith(
            execution("sql-script-task", "j1", "nightly"), execution("host-task", "h1", null));
    TaskLogic taskLogic =
        new TaskLogic(scheduler, new Caching(), true, null, List.of(sqlJobLikeResolver()));

    GetTasksResponse response = taskLogic.getAllTasks(params());

    TaskModel sqlTask =
        response.getItems().stream()
            .filter(t -> t.getTaskName().equals("sql-script-task"))
            .findFirst()
            .orElseThrow();
    assertThat(sqlTask.getDisplays()).containsExactly(new TaskDisplay("job nightly", "schema-1"));
    TaskModel hostTask =
        response.getItems().stream()
            .filter(t -> t.getTaskName().equals("host-task"))
            .findFirst()
            .orElseThrow();
    assertThat(hostTask.getDisplays()).containsExactly((TaskDisplay) null);
  }

  @Test
  void getAllTasks_groupedByTaskName_keepsDisplaysAlignedWithInstances() {
    Scheduler scheduler =
        schedulerWith(
            execution("sql-script-task", "j1", "nightly"),
            execution("sql-script-task", "j2", "hourly"));
    TaskLogic taskLogic =
        new TaskLogic(scheduler, new Caching(), true, null, List.of(sqlJobLikeResolver()));

    GetTasksResponse response = taskLogic.getAllTasks(params());

    TaskModel group = response.getItems().get(0);
    assertThat(group.getTaskInstance()).containsExactly("j1", "j2");
    assertThat(group.getDisplays())
        .containsExactly(
            new TaskDisplay("job nightly", "schema-1"), new TaskDisplay("job hourly", "schema-1"));
  }

  @Test
  void getTask_detailsCarryDisplays() {
    Scheduler scheduler = schedulerWith(execution("sql-script-task", "j1", "nightly"));
    TaskLogic taskLogic =
        new TaskLogic(scheduler, new Caching(), true, null, List.of(sqlJobLikeResolver()));

    GetTasksResponse response =
        taskLogic.getTask(
            TaskDetailsRequestParams.builder().taskName("sql-script-task").taskId("j1").build());

    assertThat(response.getItems().get(0).getDisplays())
        .containsExactly(new TaskDisplay("job nightly", "schema-1"));
  }

  @Test
  void getAllTasks_withoutResolver_hasNoDisplays() {
    Scheduler scheduler = schedulerWith(execution("sql-script-task", "j1", "nightly"));
    TaskLogic taskLogic = new TaskLogic(scheduler, new Caching(), true);

    GetTasksResponse response = taskLogic.getAllTasks(params());

    assertThat(response.getItems().get(0).getDisplays()).isNull();
  }

  @Test
  void getAllTasks_displaysSurviveTaskDataBeingStripped() {
    Scheduler scheduler = schedulerWith(execution("sql-script-task", "j1", "nightly"));
    // showData=false: taskData is cleared after mapping, displays were resolved before that
    TaskLogic taskLogic =
        new TaskLogic(scheduler, new Caching(), false, null, List.of(sqlJobLikeResolver()));

    GetTasksResponse response = taskLogic.getAllTasks(params());

    TaskModel sqlTask = response.getItems().get(0);
    assertThat(sqlTask.getTaskData()).isEmpty();
    assertThat(sqlTask.getDisplays()).containsExactly(new TaskDisplay("job nightly", "schema-1"));
  }

  @Test
  void getAllTasks_multipleResolvers_firstNonNullWins() {
    Scheduler scheduler =
        schedulerWith(
            execution("sql-script-task", "j1", "nightly"), execution("host-task", "h1", null));
    TaskDisplayResolver hostResolver =
        (taskName, taskInstance, taskData) -> new TaskDisplay("host " + taskInstance, null);
    // sqlJobLikeResolver claims only sql-script-task; hostResolver (second) catches the rest
    TaskLogic taskLogic =
        new TaskLogic(
            scheduler, new Caching(), true, null, List.of(sqlJobLikeResolver(), hostResolver));

    GetTasksResponse response = taskLogic.getAllTasks(params());

    TaskModel sqlTask =
        response.getItems().stream()
            .filter(t -> t.getTaskName().equals("sql-script-task"))
            .findFirst()
            .orElseThrow();
    assertThat(sqlTask.getDisplays()).containsExactly(new TaskDisplay("job nightly", "schema-1"));
    TaskModel hostTask =
        response.getItems().stream()
            .filter(t -> t.getTaskName().equals("host-task"))
            .findFirst()
            .orElseThrow();
    assertThat(hostTask.getDisplays()).containsExactly(new TaskDisplay("host h1", null));
  }

  @Test
  void getTask_displaysSurviveTaskDataBeingStripped() {
    Scheduler scheduler = schedulerWith(execution("sql-script-task", "j1", "nightly"));
    TaskLogic taskLogic =
        new TaskLogic(scheduler, new Caching(), false, null, List.of(sqlJobLikeResolver()));

    GetTasksResponse response =
        taskLogic.getTask(
            TaskDetailsRequestParams.builder().taskName("sql-script-task").taskId("j1").build());

    TaskModel sqlTask = response.getItems().get(0);
    assertThat(sqlTask.getTaskData()).doesNotContain((Object) "nightly");
    assertThat(sqlTask.getDisplays()).containsExactly(new TaskDisplay("job nightly", "schema-1"));
  }

  private static TaskRequestParams searchParams(String term, boolean exact) {
    return new TaskRequestParams(
        null, null, null, null, null, term, null, exact, null, null, null, null, null);
  }

  @Test
  void getAllTasks_searchTermMatchesResolvedDisplayName() {
    Scheduler scheduler = schedulerWith(execution("sql-script-task", "j1", "nightly"));
    TaskLogic taskLogic =
        new TaskLogic(scheduler, new Caching(), true, null, List.of(sqlJobLikeResolver()));

    // the row shows "job nightly" instead of the UUID — the search box has to find it by that label
    assertThat(taskLogic.getAllTasks(searchParams("job nightly", true)).getItems()).hasSize(1);
    assertThat(taskLogic.getAllTasks(searchParams("nightly", false)).getItems()).hasSize(1);
    assertThat(taskLogic.getAllTasks(searchParams("zzz", false)).getItems()).isEmpty();
  }
}
