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

/**
 * Optional hook that lets another module hide executions from the task and log APIs — for example
 * the users &amp; schemas feature, which must make sql-jobs of inaccessible schemas invisible.
 *
 * <p>The bean is only present when such a feature is active; without it everything stays visible.
 * The core module never interprets {@code schemaId}, it only forwards the request parameter.
 */
public interface ExecutionVisibilityFilter {

  /**
   * Whether {@code task_data} must be deserialized for this task name even when the UI runs with
   * task-data disabled, so that {@link #visible} can decide on the actual data.
   */
  default boolean needsTaskData(String taskName) {
    return false;
  }

  /**
   * @param taskData the deserialized task data blob, {@code null} when there is none or it could
   *     not be read
   * @param schemaId optional schema filter from the request, {@code null} when none was requested
   * @throws org.springframework.web.server.ResponseStatusException implementations backed by a
   *     session may throw 401 for anonymous callers; the core relies on the host (or the UI's own
   *     auth filter) to reject anonymous API requests before this is consulted
   */
  boolean visible(String taskName, String taskInstance, Object taskData, String schemaId);
}
