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
package no.bekk.dbscheduler.ui.users.service;

import no.bekk.dbscheduler.ui.service.ExecutionVisibilityFilter;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.sql.service.SqlJobService;

/**
 * Hides sql-job executions of schemas the signed-in user cannot access from the core task and log
 * APIs — invisible means 404/absent, never 403, matching the binary access model of the feature.
 *
 * <p>When a schema filter is active the list narrows to that schema's sql-jobs only: plain host
 * tasks have no schema and are hidden as well.
 */
public class SqlJobExecutionVisibility implements ExecutionVisibilityFilter {

  private final SqlAccessGuard guard;

  public SqlJobExecutionVisibility(SqlAccessGuard guard) {
    this.guard = guard;
  }

  @Override
  public boolean needsTaskData(String taskName) {
    return isSqlJobTask(taskName);
  }

  @Override
  public boolean visible(String taskName, String taskInstance, Object taskData, String schemaId) {
    boolean schemaFilter = schemaId != null && !schemaId.isBlank();
    if (!isSqlJobTask(taskName)) {
      return !schemaFilter;
    }
    if (taskData instanceof SqlJobData data) {
      return guard.canAccess(data.getSchemaId())
          && (!schemaFilter || schemaId.equals(data.getSchemaId()));
    }
    // Missing or unreadable blob (legacy row, corrupt data): fail closed, admin only.
    return guard.isAdmin() && !schemaFilter;
  }

  private static boolean isSqlJobTask(String taskName) {
    return SqlJobService.SQL_SCRIPT_TASK.equals(taskName)
        || SqlJobService.SQL_SCRIPT_ONCE_TASK.equals(taskName);
  }
}
