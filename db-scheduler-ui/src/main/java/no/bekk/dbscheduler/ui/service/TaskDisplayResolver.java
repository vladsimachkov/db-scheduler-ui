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

import no.bekk.dbscheduler.ui.model.TaskDisplay;

/**
 * Optional hook that lets another module attach display info to executions the core cannot
 * interpret on its own — for example sql-jobs, whose human-readable name and schema live inside the
 * task-data blob rather than in the scheduler columns.
 *
 * <p>Beans are only present when such a feature is active; without any, no display info is sent.
 * When several resolvers are registered they are consulted in order and the first non-null result
 * wins, so each feature can claim only the executions it understands. The core resolves display
 * info from the ungrouped task list, where the task-data blob is still deserialized, independently
 * of the task-data UI setting.
 */
public interface TaskDisplayResolver {

  /**
   * @param taskData the deserialized task data blob, {@code null} when there is none or it could
   *     not be read
   * @return the display info for this execution, or {@code null} when this resolver does not apply
   */
  TaskDisplay resolve(String taskName, String taskInstance, Object taskData);
}
