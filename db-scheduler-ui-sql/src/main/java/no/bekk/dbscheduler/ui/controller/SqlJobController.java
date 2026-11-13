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
package no.bekk.dbscheduler.ui.controller;

import java.time.Instant;
import java.util.List;
import no.bekk.dbscheduler.ui.sql.dto.SqlJobDto;
import no.bekk.dbscheduler.ui.sql.model.SqlJobExecution;
import no.bekk.dbscheduler.ui.sql.service.SqlJobService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only SQL job endpoints; mutating endpoints live in {@link SqlJobAdminController}. */
@RestController
@CrossOrigin
@RequestMapping("/db-scheduler-api/sql-jobs")
public class SqlJobController {

  private final SqlJobService jobs;

  public SqlJobController(SqlJobService jobs) {
    this.jobs = jobs;
  }

  @GetMapping
  public List<SqlJobDto> list() {
    return jobs.listJobs();
  }

  @GetMapping("/preview-schedule")
  public List<Instant> previewSchedule(
      @RequestParam String cron,
      @RequestParam(required = false) String timezone,
      @RequestParam(defaultValue = "3") int count) {
    return jobs.previewSchedule(cron, timezone, Math.min(Math.max(1, count), 10));
  }

  @GetMapping("/{jobId}")
  public SqlJobDto get(@PathVariable String jobId) {
    return jobs.getJob(jobId);
  }

  @GetMapping("/{jobId}/executions")
  public List<SqlJobExecution> executions(
      @PathVariable String jobId, @RequestParam(defaultValue = "20") int limit) {
    return jobs.executionsFor(jobId, limit);
  }
}
