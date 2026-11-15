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
package no.bekk.dbscheduler.ui.util.mapper;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import no.bekk.dbscheduler.ui.model.TaskDisplay;
import no.bekk.dbscheduler.ui.model.TaskModel;
import no.bekk.dbscheduler.ui.service.TaskDisplayResolver;

public class TaskMapper {

  public static List<TaskModel> mapScheduledExecutionsToTaskModel(
      List<ScheduledExecution<Object>> scheduledExecutions) {
    return scheduledExecutions.stream()
        .map(
            execution ->
                new TaskModel(
                    execution.getTaskInstance().getTaskName(),
                    Collections.singletonList(execution.getTaskInstance().getId()),
                    Collections.singletonList(execution.getData()),
                    Collections.singletonList(execution.getExecutionTime()),
                    List.of(execution.isPicked()),
                    Collections.singletonList(execution.getPickedBy()),
                    Collections.singletonList(execution.getLastSuccess()),
                    execution.getLastFailure(),
                    List.of(execution.getConsecutiveFailures()), // Modified here
                    null,
                    0,
                    null))
        .collect(Collectors.toList());
  }

  /**
   * Resolves display info for ungrouped models (one execution each) while their task-data blob is
   * still deserialized. A null resolver is a no-op; a resolver returning null for an execution
   * leaves a null hole in the parallel list.
   */
  public static List<TaskModel> applyDisplays(
      List<TaskModel> ungroupedTasks, TaskDisplayResolver resolver) {
    if (resolver == null) {
      return ungroupedTasks;
    }
    for (TaskModel task : ungroupedTasks) {
      TaskDisplay display =
          resolver.resolve(
              task.getTaskName(),
              task.getTaskInstance().get(0),
              task.getTaskData().isEmpty() ? null : task.getTaskData().get(0));
      task.setDisplays(Collections.singletonList(display));
    }
    return ungroupedTasks;
  }

  public static List<TaskModel> groupTasks(List<TaskModel> tasks) {
    return tasks.stream().collect(Collectors.groupingBy(TaskModel::getTaskName)).values().stream()
        .map(
            taskModels -> {
              // Sort members so the flattened parallel-lists are in a stable order across
              // requests (groupingBy iteration order is otherwise non-deterministic).
              taskModels.sort(
                  Comparator.comparing(
                          TaskMapper::firstExecutionTime, Comparator.nullsLast(Instant::compareTo))
                      .thenComparing(
                          TaskMapper::firstTaskInstance, Comparator.nullsLast(String::compareTo)));
              TaskModel taskModel = taskModels.get(0);
              taskModel.setTaskInstance(
                  taskModels.stream()
                      .map(TaskModel::getTaskInstance)
                      .flatMap(List::stream)
                      .collect(Collectors.toList()));
              taskModel.setTaskData(
                  taskModels.stream()
                      .map(TaskModel::getTaskData)
                      .flatMap(List::stream)
                      .collect(Collectors.toList()));
              taskModel.setExecutionTime(
                  taskModels.stream()
                      .map(TaskModel::getExecutionTime)
                      .flatMap(List::stream)
                      .collect(Collectors.toList()));
              taskModel.setPicked(
                  taskModels.stream()
                      .map(TaskModel::getPicked)
                      .flatMap(List::stream)
                      .collect(Collectors.toList()));
              taskModel.setPickedBy(
                  taskModels.stream()
                      .map(TaskModel::getPickedBy)
                      .flatMap(List::stream)
                      .collect(Collectors.toList()));
              taskModel.setLastSuccess(
                  taskModels.stream()
                      .map(TaskModel::getLastSuccess)
                      .flatMap(List::stream)
                      .collect(Collectors.toList()));
              taskModel.setLastFailure(
                  taskModels.stream()
                      .map(TaskModel::getLastFailure)
                      .filter(Objects::nonNull)
                      .max(Instant::compareTo)
                      .orElse(null));
              taskModel.setConsecutiveFailures(
                  taskModels.stream()
                      .map(TaskModel::getConsecutiveFailures)
                      .flatMap(List::stream)
                      .collect(Collectors.toList())); // Modified here
              // keep displays aligned with the merged instances; a member without display
              // info contributes a null hole, and an all-null merge stays null
              if (taskModels.stream().anyMatch(t -> t.getDisplays() != null)) {
                taskModel.setDisplays(
                    taskModels.stream()
                        .flatMap(
                            t ->
                                t.getDisplays() == null
                                    ? Stream.of((TaskDisplay) null)
                                    : t.getDisplays().stream())
                        .collect(Collectors.toList()));
              } else {
                taskModel.setDisplays(null);
              }
              return taskModel;
            })
        .sorted(Comparator.comparing(TaskModel::getTaskName))
        .collect(Collectors.toList());
  }

  private static Instant firstExecutionTime(TaskModel task) {
    return task.getExecutionTime().stream()
        .filter(Objects::nonNull)
        .min(Instant::compareTo)
        .orElse(null);
  }

  private static String firstTaskInstance(TaskModel task) {
    return task.getTaskInstance().stream().filter(Objects::nonNull).findFirst().orElse(null);
  }

  public static List<TaskModel> mapAllExecutionsToTaskModelUngrouped(
      List<ScheduledExecution<Object>> scheduledExecutions) {
    return mapScheduledExecutionsToTaskModel(scheduledExecutions);
  }
}
