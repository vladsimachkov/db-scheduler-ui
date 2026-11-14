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

import java.time.Instant;

/**
 * A registered target-database connection. {@code password} holds either plaintext (no encryption
 * key configured) or an {@code enc:v1:...} ciphertext; it must never be returned by the API.
 */
public record SqlJobConnection(
    String id,
    String name,
    /** Owning schema id; {@code null} on rows created before the users feature (admin-only). */
    String schemaId,
    SqlDialect dialect,
    String jdbcUrl,
    String driverClass,
    String username,
    String password,
    String propertiesJson,
    Instant createdTime,
    Instant updatedTime) {}
