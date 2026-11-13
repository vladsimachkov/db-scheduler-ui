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

import jakarta.validation.Valid;
import no.bekk.dbscheduler.ui.sql.dto.SqlJobDto;
import no.bekk.dbscheduler.ui.sql.dto.SqlJobRequest;
import no.bekk.dbscheduler.ui.sql.service.SqlJobService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mutating SQL job endpoints. Registered only when {@code db-scheduler-ui.read-only} is not true
 * (same gate as {@code TaskAdminController}).
 */
@RestController
@CrossOrigin
@RequestMapping("/db-scheduler-api/sql-jobs")
public class SqlJobAdminController {

  private final SqlJobService jobs;

  public SqlJobAdminController(SqlJobService jobs) {
    this.jobs = jobs;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SqlJobDto create(@Valid @RequestBody SqlJobRequest request) {
    return jobs.create(request);
  }

  @PutMapping("/{jobId}")
  public SqlJobDto update(@PathVariable String jobId, @Valid @RequestBody SqlJobRequest request) {
    return jobs.update(jobId, request);
  }

  @PostMapping("/{jobId}/run-now")
  public void runNow(@PathVariable String jobId) {
    jobs.runNow(jobId);
  }

  @DeleteMapping("/{jobId}")
  public void delete(@PathVariable String jobId) {
    jobs.delete(jobId);
  }
}
