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

import static no.bekk.dbscheduler.ui.util.QueryUtils.filterExecutions;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.TaskInstanceId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import no.bekk.dbscheduler.ui.model.GetTasksResponse;
import no.bekk.dbscheduler.ui.model.PollResponse;
import no.bekk.dbscheduler.ui.model.TaskDetailsRequestParams;
import no.bekk.dbscheduler.ui.model.TaskModel;
import no.bekk.dbscheduler.ui.model.TaskRequestParams;
import no.bekk.dbscheduler.ui.util.Caching;
import no.bekk.dbscheduler.ui.util.QueryUtils;
import no.bekk.dbscheduler.ui.util.mapper.TaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TaskLogic {

  private static final Logger log = LoggerFactory.getLogger(TaskLogic.class);

  private final Scheduler scheduler;
  private final Caching caching;
  private final boolean showData;
  private final ExecutionVisibilityFilter visibility;
  private final List<TaskDisplayResolver> displayResolvers;

  public TaskLogic(Scheduler scheduler, Caching caching, boolean showData) {
    this(scheduler, caching, showData, null, List.of());
  }

  public TaskLogic(
      Scheduler scheduler,
      Caching caching,
      boolean showData,
      ExecutionVisibilityFilter visibility) {
    this(scheduler, caching, showData, visibility, List.of());
  }

  public TaskLogic(
      Scheduler scheduler,
      Caching caching,
      boolean showData,
      ExecutionVisibilityFilter visibility,
      List<TaskDisplayResolver> displayResolvers) {
    this.scheduler = scheduler;
    this.caching = caching;
    this.showData = showData;
    this.visibility = visibility;
    this.displayResolvers = displayResolvers;
  }

  private boolean visible(String taskName, String taskInstance, Object taskData, String schemaId) {
    return visibility == null || visibility.visible(taskName, taskInstance, taskData, schemaId);
  }

  /** Filters a list of ungrouped task models (one execution each) before grouping. */
  private List<TaskModel> filterVisible(List<TaskModel> ungroupedTasks, String schemaId) {
    if (visibility == null) {
      return ungroupedTasks;
    }
    return ungroupedTasks.stream()
        .filter(
            task ->
                visible(
                    task.getTaskName(),
                    task.getTaskInstance().get(0),
                    task.getTaskData().isEmpty() ? null : task.getTaskData().get(0),
                    schemaId))
        .collect(Collectors.toList());
  }

  public void runTaskNow(String taskId, String taskName, Instant scheduleTime) {
    Optional<ScheduledExecution<Object>> scheduledExecutionOpt =
        scheduler.getScheduledExecution(TaskInstanceId.of(taskName, taskId));

    if (scheduledExecutionOpt.isPresent()
        && !scheduledExecutionOpt.get().isPicked()
        && visible(taskName, taskId, scheduledExecutionOpt.get().getData(), null)) {
      scheduler.reschedule(
          scheduledExecutionOpt.get().getTaskInstance(),
          scheduleTime != null ? scheduleTime : Instant.now());
    } else {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "No ScheduledExecution found for taskName: " + taskName + ", taskId: " + taskId);
    }
  }

  public void runTaskGroupNow(String taskName, boolean onlyFailed) {
    caching
        .getExecutionsFromCacheOrDB(false, scheduler)
        .forEach(
            (execution) -> {
              if ((!onlyFailed || execution.getConsecutiveFailures() > 0)
                  && taskName.equals(execution.getTaskInstance().getTaskName())
                  && visible(
                      execution.getTaskInstance().getTaskName(),
                      execution.getTaskInstance().getId(),
                      execution.getData(),
                      null)) {
                try {
                  runTaskNow(
                      execution.getTaskInstance().getId(),
                      execution.getTaskInstance().getTaskName(),
                      Instant.now());
                } catch (ResponseStatusException e) {
                  log.warn("Failed to run task: {}", e.getMessage());
                }
              }
            });
  }

  public void deleteTask(String taskId, String taskName) {
    Optional<ScheduledExecution<Object>> scheduledExecutionOpt =
        scheduler.getScheduledExecution(TaskInstanceId.of(taskName, taskId));

    if (scheduledExecutionOpt.isPresent()
        && visible(taskName, taskId, scheduledExecutionOpt.get().getData(), null)) {
      TaskInstanceId taskInstance = scheduledExecutionOpt.get().getTaskInstance();
      scheduler.cancel(taskInstance);
    } else {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "No ScheduledExecution found for taskName: " + taskName + ", taskId: " + taskId);
    }
  }

  public GetTasksResponse getAllTasks(TaskRequestParams params) {
    List<ScheduledExecution<Object>> executions =
        caching.getExecutionsFromCacheOrDB(params.isRefresh(), scheduler);

    List<TaskModel> tasks = TaskMapper.mapAllExecutionsToTaskModelUngrouped(executions);
    tasks = TaskMapper.applyDisplays(tasks, displayResolvers);

    tasks = filterVisible(tasks, params.getSchemaId());
    tasks =
        QueryUtils.searchByTaskName(
            tasks, params.getSearchTermTaskName(), params.isTaskNameExactMatch());
    tasks =
        QueryUtils.searchByTaskInstance(
            tasks, params.getSearchTermTaskInstance(), params.isTaskInstanceExactMatch());
    if (!showData) {
      tasks.forEach(e -> e.setTaskData(List.of()));
    }
    tasks = TaskMapper.groupTasks(tasks);
    tasks =
        QueryUtils.sortTasks(
            QueryUtils.filterTasks(tasks, params.getFilter()), params.getSorting(), params.isAsc());
    List<TaskModel> pagedTasks =
        QueryUtils.paginate(tasks, params.getPageNumber(), params.getSize());
    return new GetTasksResponse(
        tasks.size(), pagedTasks, QueryUtils.effectivePageSize(params.getSize()));
  }

  public GetTasksResponse getTask(TaskDetailsRequestParams params) {
    List<ScheduledExecution<Object>> executions =
        caching.getExecutionsFromCacheOrDB(params.isRefresh(), scheduler);

    List<TaskModel> tasks =
        params.getTaskId() != null
            ? TaskMapper.mapAllExecutionsToTaskModelUngrouped(executions).stream()
                .filter(
                    task -> {
                      return task.getTaskName().equals(params.getTaskName())
                          && task.getTaskInstance().get(0).equals(params.getTaskId());
                    })
                .collect(Collectors.toList())
            : TaskMapper.mapAllExecutionsToTaskModelUngrouped(executions).stream()
                .filter(
                    task -> {
                      return task.getTaskName().equals(params.getTaskName());
                    })
                .collect(Collectors.toList());
    tasks = TaskMapper.applyDisplays(tasks, displayResolvers);
    tasks = filterVisible(tasks, params.getSchemaId());
    if (tasks.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "No tasks found for taskName: "
              + params.getTaskName()
              + ", taskId: "
              + params.getTaskId());
    }
    tasks =
        QueryUtils.searchByTaskName(
            tasks, params.getSearchTermTaskName(), params.isTaskNameExactMatch());
    tasks =
        QueryUtils.searchByTaskInstance(
            tasks, params.getSearchTermTaskInstance(), params.isTaskInstanceExactMatch());
    tasks =
        QueryUtils.sortTasks(
            QueryUtils.filterTasks(tasks, params.getFilter()), params.getSorting(), params.isAsc());
    if (!showData) {
      List<Object> list =
          new ArrayList<>() {
            {
              add(null);
            }
          };
      tasks.forEach(e -> e.setTaskData(list));
    }

    List<TaskModel> pagedTasks =
        QueryUtils.paginate(tasks, params.getPageNumber(), params.getSize());
    return new GetTasksResponse(
        tasks.size(), pagedTasks, QueryUtils.effectivePageSize(params.getSize()));
  }

  public PollResponse pollTasks(TaskDetailsRequestParams params) {
    List<ScheduledExecution<Object>> allTasks =
        filterExecutions(
            caching.getExecutionsFromDBWithoutUpdatingCache(scheduler),
            TaskRequestParams.TaskFilter.ALL,
            params.getTaskName());
    if (visibility != null) {
      allTasks =
          allTasks.stream()
              .filter(
                  task ->
                      visible(
                          task.getTaskInstance().getTaskName(),
                          task.getTaskInstance().getId(),
                          task.getData(),
                          params.getSchemaId()))
              .collect(Collectors.toList());
    }

    Set<String> newTaskNames = new HashSet<>();
    Set<String> newFailureTaskNames = new HashSet<>();
    Set<String> newRunningTaskNames = new HashSet<>();

    int stoppedFailing = 0;
    int finishedRunning = 0;

    for (ScheduledExecution<Object> task : allTasks) {
      String taskName = task.getTaskInstance().getTaskName();
      String status = caching.getStatus(task);
      String cachedStatus = caching.getStatusFromCache(task.getTaskInstance());

      if (cachedStatus == null) {
        handleNewTask(
            params, newTaskNames, newFailureTaskNames, newRunningTaskNames, taskName, status);
        continue;
      }

      if (!cachedStatus.equals(status)) {
        StatusDelta delta =
            handleStatusChange(
                params, newFailureTaskNames, newRunningTaskNames, taskName, status, cachedStatus);
        if (delta.stoppedFailing()) {
          stoppedFailing++;
        }
        if (delta.finishedRunning()) {
          finishedRunning++;
        }
      }
    }

    return new PollResponse(
        newFailureTaskNames.size(),
        newRunningTaskNames.size(),
        newTaskNames.size(),
        stoppedFailing,
        finishedRunning);
  }

  private void handleNewTask(
      TaskDetailsRequestParams params,
      Set<String> newTaskNames,
      Set<String> newFailureTaskNames,
      Set<String> newRunningTaskNames,
      String taskName,
      String status) {
    if (newTaskNames.contains(taskName) && params.getTaskName() == null) {
      return;
    }

    newTaskNames.add(taskName);
    if (status.charAt(0) == '1') {
      newFailureTaskNames.add(taskName);
    }
    if (status.charAt(1) == '1') {
      newRunningTaskNames.add(taskName);
    }
  }

  private StatusDelta handleStatusChange(
      TaskDetailsRequestParams params,
      Set<String> newFailureTaskNames,
      Set<String> newRunningTaskNames,
      String taskName,
      String status,
      String cachedStatus) {
    boolean stoppedFailing = false;
    boolean finishedRunning = false;

    if (cachedStatus.charAt(0) == '0'
        && status.charAt(0) == '1'
        && (!newFailureTaskNames.contains(taskName) || params.getTaskName() != null)) {
      newFailureTaskNames.add(taskName);
    }
    if (cachedStatus.charAt(0) == '1' && status.charAt(0) == '0') {
      stoppedFailing = true;
    }

    if (cachedStatus.charAt(1) == '0'
        && status.charAt(1) == '1'
        && (!newRunningTaskNames.contains(taskName) || params.getTaskName() != null)) {
      newRunningTaskNames.add(taskName);
    }
    if (cachedStatus.charAt(1) == '1' && status.charAt(1) == '0') {
      finishedRunning = true;
    }

    return new StatusDelta(stoppedFailing, finishedRunning);
  }

  private record StatusDelta(boolean stoppedFailing, boolean finishedRunning) {}
}
