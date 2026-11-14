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
package no.bekk.dbscheduler.ui.users.auth;

import java.util.Set;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.repo.GrantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Schema visibility derived from the session principal and the {@code sql_schema_grants} rows. */
public class SessionAccessGuard implements SqlAccessGuard {

  private final GrantRepository grants;

  public SessionAccessGuard(GrantRepository grants) {
    this.grants = grants;
  }

  @Override
  public AuthPrincipal current() {
    AuthPrincipal principal = AuthContext.get();
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return principal;
  }

  @Override
  public boolean isAdmin() {
    return current().admin();
  }

  @Override
  public boolean canAccess(String schemaId) {
    AuthPrincipal principal = current();
    if (principal.admin()) {
      return true;
    }
    return schemaId != null && grants.listSchemaIdsByUser(principal.userId()).contains(schemaId);
  }

  @Override
  public void require(String schemaId) {
    if (!canAccess(schemaId)) {
      // 404, not 403: a schema the caller cannot see must not even be confirmed to exist
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
    }
  }

  @Override
  public Set<String> accessibleSchemaIds() {
    AuthPrincipal principal = current();
    return principal.admin() ? null : grants.listSchemaIdsByUser(principal.userId());
  }
}
