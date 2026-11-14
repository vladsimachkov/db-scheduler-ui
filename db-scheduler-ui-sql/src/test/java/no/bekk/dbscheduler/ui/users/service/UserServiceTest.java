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
package no.bekk.dbscheduler.ui.users.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.users.crypto.PasswordHasher;
import no.bekk.dbscheduler.ui.users.dto.PasswordResetRequest;
import no.bekk.dbscheduler.ui.users.dto.UserCreateRequest;
import no.bekk.dbscheduler.ui.users.dto.UserUpdateRequest;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.GrantRepository;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Admin-only user management with the self-lockout and last-admin guards. */
class UserServiceTest {

  private final UserRepository users = mock(UserRepository.class);
  private final GrantRepository grants = mock(GrantRepository.class);
  private final PasswordHasher hasher = mock(PasswordHasher.class);

  private UserService serviceAs(String userId, boolean admin) {
    SqlAccessGuard guard =
        new SqlAccessGuard() {
          @Override
          public AuthPrincipal current() {
            return new AuthPrincipal(userId, userId, admin);
          }

          @Override
          public boolean isAdmin() {
            return admin;
          }

          @Override
          public boolean canAccess(String schemaId) {
            return admin;
          }

          @Override
          public void require(String schemaId) {}

          @Override
          public java.util.Set<String> accessibleSchemaIds() {
            return admin ? null : java.util.Set.of();
          }
        };
    return new UserService(users, grants, hasher, guard);
  }

  private static UserAccount user(String id, String username, boolean admin, boolean enabled) {
    Instant now = Instant.now();
    return new UserAccount(id, username, "stored-hash", admin, enabled, now, now);
  }

  @Test
  void allEndpointsRejectNonAdmin() {
    UserService bob = serviceAs("u2", false);

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(bob::list)
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.create(new UserCreateRequest("x", "pw", false, null)))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.update("u1", new UserUpdateRequest(false, null)))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.delete("u1"))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.resetPassword("u1", new PasswordResetRequest("pw")))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    verify(users, never()).insert(any());
  }

  @Test
  void concurrentDuplicateCreateMapsTo409() {
    when(hasher.hash(anyString())).thenReturn("new-hash");
    org.mockito.Mockito.doThrow(new org.springframework.dao.DuplicateKeyException("dup"))
        .when(users)
        .insert(any());

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(
            () -> serviceAs("u1", true).create(new UserCreateRequest("bob", "pw", null, null)))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);
  }

  @Test
  void createHashesPasswordAndRejectsDuplicateUsername() {
    when(hasher.hash("pw")).thenReturn("new-hash");

    serviceAs("u1", true).create(new UserCreateRequest(" bob ", "pw", false, null));

    ArgumentCaptor<UserAccount> inserted = ArgumentCaptor.forClass(UserAccount.class);
    verify(users).insert(inserted.capture());
    assertThat(inserted.getValue().username()).isEqualTo("bob");
    assertThat(inserted.getValue().passwordHash()).isEqualTo("new-hash");
    assertThat(inserted.getValue().enabled()).isTrue();

    when(users.findByUsername("bob")).thenReturn(Optional.of(user("u9", "bob", false, true)));
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(
            () -> serviceAs("u1", true).create(new UserCreateRequest("bob", "pw", false, null)))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);
  }

  @Test
  void cannotDemoteOrDisableYourself() {
    when(users.findById("u1")).thenReturn(Optional.of(user("u1", "admin", true, true)));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> serviceAs("u1", true).update("u1", new UserUpdateRequest(false, null)))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> serviceAs("u1", true).update("u1", new UserUpdateRequest(null, false)))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST);
    verify(users, never()).update(any());
  }

  @Test
  void cannotDemoteOrDisableTheLastEnabledAdmin() {
    when(users.findById("u3")).thenReturn(Optional.of(user("u3", "solo", true, true)));
    when(users.findAll()).thenReturn(List.of(user("u3", "solo", true, true)));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> serviceAs("u1", true).update("u3", new UserUpdateRequest(false, null)))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> serviceAs("u1", true).update("u3", new UserUpdateRequest(null, false)))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);
  }

  @Test
  void updateSucceedsWhenAnotherEnabledAdminRemains() {
    when(users.findById("u3")).thenReturn(Optional.of(user("u3", "other", true, true)));
    when(users.findAll())
        .thenReturn(List.of(user("u1", "admin", true, true), user("u3", "other", true, true)));

    var updated = serviceAs("u1", true).update("u3", new UserUpdateRequest(false, null));

    assertThat(updated.admin()).isFalse();
    verify(users).update(any());
  }

  @Test
  void deleteBlocksSelfAndLastAdminButRemovesGrantsOtherwise() {
    when(users.findById("u1")).thenReturn(Optional.of(user("u1", "admin", true, true)));
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> serviceAs("u1", true).delete("u1"))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST);

    when(users.findById("u3")).thenReturn(Optional.of(user("u3", "solo", true, true)));
    when(users.findAll()).thenReturn(List.of(user("u3", "solo", true, true)));
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> serviceAs("u1", true).delete("u3"))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);

    when(users.findById("u4")).thenReturn(Optional.of(user("u4", "bob", false, true)));
    serviceAs("u1", true).delete("u4");
    verify(grants).deleteByUser("u4");
    verify(users).deleteById("u4");
  }

  @Test
  void resetPasswordStoresHashNotPlaintext() {
    when(users.findById("u4")).thenReturn(Optional.of(user("u4", "bob", false, true)));
    when(hasher.hash(anyString())).thenReturn("fresh-hash");

    serviceAs("u1", true).resetPassword("u4", new PasswordResetRequest("new-pw"));

    verify(users).updatePasswordHash(eq("u4"), eq("fresh-hash"), any(Instant.class));
  }
}
