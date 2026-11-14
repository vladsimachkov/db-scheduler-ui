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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.repo.GrantRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** The shipped guard: session principal + grant rows decide visibility; anonymous answers 401. */
class SessionAccessGuardTest {

  private final GrantRepository grants = mock(GrantRepository.class);
  private final SessionAccessGuard guard = new SessionAccessGuard(grants);

  @AfterEach
  void clearContext() {
    AuthContext.clear();
  }

  @Test
  void anonymousIsUnauthorized() {
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> guard.current())
        .matches(e -> e.getStatusCode() == HttpStatus.UNAUTHORIZED);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> guard.canAccess("s1"))
        .matches(e -> e.getStatusCode() == HttpStatus.UNAUTHORIZED);
  }

  @Test
  void adminSeesEverythingIncludingUnassigned() {
    AuthContext.set(new AuthPrincipal("u1", "admin", true));

    assertThat(guard.isAdmin()).isTrue();
    assertThat(guard.canAccess(null)).isTrue();
    assertThat(guard.canAccess("any")).isTrue();
    assertThat(guard.accessibleSchemaIds()).isNull(); // null = all
  }

  @Test
  void nonAdminSeesOnlyGrantedSchemas() {
    AuthContext.set(new AuthPrincipal("u2", "bob", false));
    when(grants.listSchemaIdsByUser("u2")).thenReturn(new HashSet<>(Set.of("s1")));

    assertThat(guard.canAccess("s1")).isTrue();
    assertThat(guard.canAccess("s2")).isFalse();
    // unassigned resources are admin-only
    assertThat(guard.canAccess(null)).isFalse();
    assertThat(guard.accessibleSchemaIds()).containsExactly("s1");
  }

  @Test
  void requireOnUngrantedSchemaAnswers404Not403() {
    AuthContext.set(new AuthPrincipal("u2", "bob", false));
    when(grants.listSchemaIdsByUser("u2")).thenReturn(new HashSet<>());

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> guard.require("s2"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
  }
}
