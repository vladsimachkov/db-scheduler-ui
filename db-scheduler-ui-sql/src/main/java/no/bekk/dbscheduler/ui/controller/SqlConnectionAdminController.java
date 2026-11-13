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
import no.bekk.dbscheduler.ui.sql.dto.SqlConnectionDto;
import no.bekk.dbscheduler.ui.sql.dto.SqlConnectionRequest;
import no.bekk.dbscheduler.ui.sql.service.SqlConnectionService;
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

/** Mutating connection endpoints; absent when {@code db-scheduler-ui.read-only=true}. */
@RestController
@CrossOrigin
@RequestMapping("/db-scheduler-api/sql-connections")
public class SqlConnectionAdminController {

  private final SqlConnectionService connections;

  public SqlConnectionAdminController(SqlConnectionService connections) {
    this.connections = connections;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SqlConnectionDto create(@Valid @RequestBody SqlConnectionRequest request) {
    return connections.create(request);
  }

  @PutMapping("/{id}")
  public SqlConnectionDto update(
      @PathVariable String id, @Valid @RequestBody SqlConnectionRequest request) {
    return connections.update(id, request);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    connections.delete(id);
  }
}
