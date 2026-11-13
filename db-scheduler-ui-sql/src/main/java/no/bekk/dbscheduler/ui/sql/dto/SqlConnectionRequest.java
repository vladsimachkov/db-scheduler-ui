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
package no.bekk.dbscheduler.ui.sql.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;

/**
 * Create/update payload for a connection. On update, a blank {@code password} keeps the stored one.
 */
public record SqlConnectionRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull SqlDialect dialect,
    @NotBlank @Size(max = 2000) String jdbcUrl,
    @Size(max = 300) String driverClass,
    @Size(max = 300) String username,
    @Size(max = 1000) String password,
    Boolean clearPassword,
    @Size(max = 4000) String propertiesJson) {}
