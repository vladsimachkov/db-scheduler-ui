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
package no.bekk.dbscheduler.ui.util;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import no.bekk.dbscheduler.ui.model.TaskDisplay;
import no.bekk.dbscheduler.ui.model.TaskModel;
import no.bekk.dbscheduler.ui.model.TaskRequestParams;
import no.bekk.dbscheduler.ui.model.TaskRequestParams.TaskFilter;
import no.bekk.dbscheduler.ui.model.TaskRequestParams.TaskSort;

public class QueryUtils {

  private static final int MAX_PAGE_SIZE = 500;

  /** The page size actually applied by {@link #paginate}; use it for page-count math. */
  public static int effectivePageSize(int pageSize) {
    return Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
  }

  public static <T> List<T> paginate(List<T> allItems, int pageNumber, int pageSize) {
    int safePageNumber = Math.max(0, pageNumber);
    int safePageSize = effectivePageSize(pageSize);
    long startIndex = (long) safePageNumber * safePageSize;
    if (startIndex >= allItems.size()) {
      return new ArrayList<>();
    }
    int endIndex = (int) Math.min(startIndex + safePageSize, allItems.size());
    return allItems.subList((int) startIndex, endIndex);
  }

  public static List<TaskModel> filterTasks(
      List<TaskModel> tasks, TaskRequestParams.TaskFilter filter) {
    return tasks.stream()
        .filter(
            task -> {
              switch (filter) {
                case FAILED:
                  return task.getConsecutiveFailures().stream().anyMatch(failures -> failures != 0);
                case RUNNING:
                  return task.getPicked().stream().anyMatch(Boolean::booleanValue);
                case SCHEDULED:
                  return IntStream.range(0, task.getPicked().size())
                      .anyMatch(
                          i ->
                              !task.getPicked().get(i)
                                  && task.getConsecutiveFailures().get(i) == 0);
                default:
                  return true;
              }
            })
        .collect(Collectors.toList());
  }

  public static List<ScheduledExecution<Object>> filterExecutions(
      List<ScheduledExecution<Object>> executions, TaskFilter filter, String taskName) {
    return executions.stream()
        .filter(
            execution -> {
              if (taskName != null && !taskName.equals(execution.getTaskInstance().getTaskName())) {
                return false;
              }
              switch (filter) {
                case FAILED:
                  return execution.getConsecutiveFailures() != 0;
                case RUNNING:
                  return execution.isPicked();
                case SCHEDULED:
                  return execution.getConsecutiveFailures() == 0 && !execution.isPicked();
                default:
                  return true;
              }
            })
        .collect(Collectors.toList());
  }

  public static List<TaskModel> sortTasks(List<TaskModel> tasks, TaskSort sortType, boolean isAsc) {
    if (sortType == TaskSort.NAME) {
      Comparator<TaskModel> compareTasks = Comparator.comparing(TaskModel::getTaskName);
      tasks.sort(isAsc ? compareTasks : compareTasks.reversed());
    } else if (sortType == TaskSort.DEFAULT) {
      Comparator<TaskModel> comparator =
          Comparator.comparing(
              task -> task.getExecutionTime().stream().min(Instant::compareTo).orElse(Instant.MAX),
              Comparator.nullsLast(Instant::compareTo));
      tasks.sort(isAsc ? comparator : comparator.reversed());
    }
    return tasks;
  }

  public static List<TaskModel> searchByTaskName(
      List<TaskModel> tasks, String searchTermTaskName, boolean isExactMatch) {
    return search(tasks, searchTermTaskName, true, isExactMatch);
  }

  public static List<TaskModel> searchByTaskInstance(
      List<TaskModel> tasks, String searchTermTaskInstance, boolean isExactMatch) {
    return search(tasks, searchTermTaskInstance, false, isExactMatch);
  }

  public static List<TaskModel> search(
      List<TaskModel> tasks, String searchTerm, boolean isTaskNameSearch, boolean isExactMatch) {
    if (searchTerm == null || searchTerm.trim().isEmpty()) {
      return tasks;
    }

    return tasks.stream()
        .filter(
            task -> {
              String lowerCaseTerm = searchTerm.toLowerCase();

              boolean isTermPresent;
              if (isTaskNameSearch) {
                isTermPresent = matchesTerm(task.getTaskName(), lowerCaseTerm, isExactMatch);
                if (!isTermPresent && task.getDisplays() != null) {
                  // the display name (e.g. a sql job's own name) is what the row shows, so the
                  // name search has to match it too — any instance of the group can match
                  isTermPresent =
                      task.getDisplays().stream()
                          .filter(Objects::nonNull)
                          .map(TaskDisplay::displayName)
                          .filter(Objects::nonNull)
                          .anyMatch(name -> matchesTerm(name, lowerCaseTerm, isExactMatch));
                }
              } else {
                isTermPresent =
                    task.getTaskInstance().stream()
                        .anyMatch(
                            instance ->
                                instance != null
                                    && (isExactMatch
                                        ? instance.equalsIgnoreCase(lowerCaseTerm)
                                        : instance.toLowerCase().contains(lowerCaseTerm)));
              }

              return isTermPresent;
            })
        .collect(Collectors.toList());
  }

  private static boolean matchesTerm(String value, String lowerCaseTerm, boolean isExactMatch) {
    return isExactMatch
        ? value.equalsIgnoreCase(lowerCaseTerm)
        : value.toLowerCase().contains(lowerCaseTerm);
  }

  public static String logSearchCondition(
      String searchTerm, Map<String, Object> params, boolean isTaskName, boolean isExactMatch) {
    StringBuilder conditions = new StringBuilder();
    List<String> termConditions = new ArrayList<>();
    String termKey = "searchTerm" + (isTaskName ? "TaskName" : "TaskInstance");
    params.put(termKey, isExactMatch ? searchTerm : "%" + searchTerm + "%");
    String condition =
        isTaskName
            ? "LOWER(task_name) " + (isExactMatch ? "=" : "LIKE") + " LOWER(:" + termKey + ")"
            : "LOWER(task_instance) " + (isExactMatch ? "=" : "LIKE") + " LOWER(:" + termKey + ")";
    termConditions.add(condition);

    return conditions.append(String.join(" AND ", termConditions)).toString();
  }
}
