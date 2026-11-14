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

import java.util.List;
import no.bekk.dbscheduler.ui.users.dto.SchemaDto;
import no.bekk.dbscheduler.ui.users.service.SchemaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoint backing the schema pickers: returns the schemas the signed-in user may see.
 * Mutating endpoints live in {@link SchemaAdminController}.
 */
@RestController
@RequestMapping("/db-scheduler-api/sql-schemas")
public class SchemaController {

  private final SchemaService schemas;

  public SchemaController(SchemaService schemas) {
    this.schemas = schemas;
  }

  @GetMapping
  public List<SchemaDto> list() {
    return schemas.listVisible();
  }
}
