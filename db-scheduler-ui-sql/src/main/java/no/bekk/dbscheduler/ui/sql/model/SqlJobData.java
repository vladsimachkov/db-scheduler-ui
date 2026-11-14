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
package no.bekk.dbscheduler.ui.sql.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.kagkarlsson.scheduler.task.helper.ScheduleAndData;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import com.github.kagkarlsson.scheduler.task.schedule.Schedules;
import java.io.Serializable;
import java.util.List;

/**
 * The full definition of a SQL job, carried inside the scheduler's {@code task_data} column.
 *
 * <p>Implements {@link ScheduleAndData} so each recurring instance owns its cron schedule: the task
 * <em>definition</em> is registered once at startup, while jobs (with their own cron) are created
 * and edited at runtime through the REST API.
 *
 * <p>Java-serialized: keep {@code serialVersionUID} fixed and only ever add fields.
 */
public class SqlJobData implements ScheduleAndData, Serializable {

  private static final long serialVersionUID = 1L;

  private final String jobId;
  private final String name;
  private final String description;
  private final String connectionId;

  /** Legacy single-script source, kept for deserialization of older {@code task_data} blobs. */
  private final String script;

  /**
   * Ordered scripts executed one after another; {@code null} on blobs persisted before the list.
   */
  private final List<String> scripts;

  private final int timeoutSeconds;
  private final String cronExpression;

  /**
   * Vestigial: jobs always fire in the server's time zone. The field is kept only so that
   * already-persisted {@code task_data} blobs deserialize cleanly (this class is Java-serialized
   * with a fixed serialVersionUID) — it is never read and never set for new or edited jobs.
   */
  private final String zoneId;

  public SqlJobData(
      String jobId,
      String name,
      String description,
      String connectionId,
      String script,
      List<String> scripts,
      int timeoutSeconds,
      String cronExpression,
      String zoneId) {
    this.jobId = jobId;
    this.name = name;
    this.description = description;
    this.connectionId = connectionId;
    this.script = script;
    this.scripts = scripts;
    this.timeoutSeconds = timeoutSeconds;
    this.cronExpression = cronExpression;
    this.zoneId = zoneId;
  }

  public String getJobId() {
    return jobId;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getConnectionId() {
    return connectionId;
  }

  public String getScript() {
    return script;
  }

  /** The ordered scripts to run: the list when present, otherwise the legacy single script. */
  public List<String> getScripts() {
    if (scripts != null && !scripts.isEmpty()) {
      return scripts;
    }
    return script != null && !script.isBlank() ? List.of(script) : List.of();
  }

  public int getTimeoutSeconds() {
    return timeoutSeconds;
  }

  public String getCronExpression() {
    return cronExpression;
  }

  public boolean isRecurring() {
    return cronExpression != null && !cronExpression.isBlank();
  }

  public Schedule toSchedule() {
    if (!isRecurring()) {
      return null;
    }
    // No zone argument: the scheduler falls back to the server's default time zone.
    return Schedules.cron(cronExpression);
  }

  /**
   * Derived from the cron fields; hidden from Jackson — the core UI serializes the raw task data
   * object into {@code taskData} responses, and a {@code Schedule} there is noise at best.
   */
  @Override
  @JsonIgnore
  public Schedule getSchedule() {
    return toSchedule();
  }

  /**
   * Required by {@link ScheduleAndData}, but {@code this} would be a self-reference for Jackson
   * (the core UI bean-serializes task data) — hence ignored.
   */
  @Override
  @JsonIgnore
  public Object getData() {
    return this;
  }
}
