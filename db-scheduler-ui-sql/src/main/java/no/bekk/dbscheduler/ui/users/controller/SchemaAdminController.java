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
package no.bekk.dbscheduler.ui.users.controller;

import jakarta.validation.Valid;
import java.util.List;
import no.bekk.dbscheduler.ui.users.dto.SchemaDto;
import no.bekk.dbscheduler.ui.users.dto.SchemaRequest;
import no.bekk.dbscheduler.ui.users.service.SchemaService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Schema CRUD and the grant panel; every call is admin-enforced in {@link SchemaService} and the
 * whole controller is absent when {@code db-scheduler-ui.read-only=true}.
 */
@RestController
@RequestMapping("/db-scheduler-api/sql-schemas")
public class SchemaAdminController {

  private final SchemaService schemas;

  public SchemaAdminController(SchemaService schemas) {
    this.schemas = schemas;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public SchemaDto create(@Valid @RequestBody SchemaRequest request) {
    return schemas.create(request);
  }

  @PutMapping("/{id}")
  public SchemaDto update(@PathVariable String id, @Valid @RequestBody SchemaRequest request) {
    return schemas.update(id, request);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    schemas.delete(id);
  }

  @GetMapping("/{id}/grants")
  public List<String> grants(@PathVariable String id) {
    return schemas.listGrants(id);
  }

  @PutMapping("/{id}/grants/{username}")
  public void grant(@PathVariable String id, @PathVariable String username) {
    schemas.grant(id, username);
  }

  @DeleteMapping("/{id}/grants/{username}")
  public void revoke(@PathVariable String id, @PathVariable String username) {
    schemas.revoke(id, username);
  }
}
