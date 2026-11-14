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
package no.bekk.dbscheduler.ui.sql.service;

import java.util.Set;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;

/**
 * Per-request schema-visibility check consulted by the SQL services. The bean exists only when the
 * users feature is enabled — services keep a nullable reference and behave exactly as before when
 * it is absent.
 *
 * <p>Invisible resources answer 404, never 403, so the API does not confirm the existence of a
 * schema the caller was not granted.
 */
public interface SqlAccessGuard {

  /** The current principal; throws 401 when the request is anonymous. */
  AuthPrincipal current();

  boolean isAdmin();

  /**
   * Whether the current user may see the schema; a {@code null} schema (unassigned) is admin-only.
   */
  boolean canAccess(String schemaId);

  /** Throws 404 when the current user may not see the schema. */
  void require(String schemaId);

  /** The schema ids the current user may see, or {@code null} meaning "all" (admin). */
  Set<String> accessibleSchemaIds();
}
