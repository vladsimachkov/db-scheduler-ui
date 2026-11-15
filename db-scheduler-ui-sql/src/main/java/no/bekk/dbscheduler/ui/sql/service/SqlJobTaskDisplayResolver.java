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

import no.bekk.dbscheduler.ui.model.TaskDisplay;
import no.bekk.dbscheduler.ui.service.TaskDisplayResolver;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;

/**
 * Gives sql-job executions their human-readable job name and schema on the task APIs, so the
 * Scheduled page can show "Nightly report" instead of {@code sql-script-task} plus a UUID. The
 * discriminator is the deserialized data type, not the task name — a legacy job whose blob could
 * not be read simply gets no display info.
 */
public class SqlJobTaskDisplayResolver implements TaskDisplayResolver {

  @Override
  public TaskDisplay resolve(String taskName, String taskInstance, Object taskData) {
    if (taskData instanceof SqlJobData data) {
      return new TaskDisplay(data.getName(), data.getSchemaId());
    }
    return null;
  }
}
