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

import java.util.List;
import no.bekk.dbscheduler.ui.sql.dto.ConnectionTestResult;
import no.bekk.dbscheduler.ui.sql.dto.SqlConnectionDto;
import no.bekk.dbscheduler.ui.sql.service.SqlConnectionService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for the connection registry. Passwords are never exposed. The connectivity test
 * does not modify stored state, so it stays here even in read-only mode.
 */
@RestController
@CrossOrigin
@RequestMapping("/db-scheduler-api/sql-connections")
public class SqlConnectionController {

  private final SqlConnectionService connections;

  public SqlConnectionController(SqlConnectionService connections) {
    this.connections = connections;
  }

  @GetMapping
  public List<SqlConnectionDto> list() {
    return connections.list();
  }

  @GetMapping("/{id}")
  public SqlConnectionDto get(@PathVariable String id) {
    return connections.get(id);
  }

  @PostMapping("/{id}/test")
  public ConnectionTestResult test(@PathVariable String id) {
    return connections.test(id);
  }
}
