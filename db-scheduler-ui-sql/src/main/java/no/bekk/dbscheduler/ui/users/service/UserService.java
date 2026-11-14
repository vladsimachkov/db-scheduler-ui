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

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.users.crypto.PasswordHasher;
import no.bekk.dbscheduler.ui.users.dto.PasswordResetRequest;
import no.bekk.dbscheduler.ui.users.dto.UserCreateRequest;
import no.bekk.dbscheduler.ui.users.dto.UserDto;
import no.bekk.dbscheduler.ui.users.dto.UserUpdateRequest;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.GrantRepository;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * User administration, global-admin only (403 for everyone else). Two lockout guards keep the
 * system manageable: nobody can demote or disable their own account, and the last enabled admin
 * cannot be demoted, disabled or deleted.
 */
public class UserService {

  private final UserRepository users;
  private final GrantRepository grants;
  private final PasswordHasher hasher;
  private final SqlAccessGuard guard;

  public UserService(
      UserRepository users, GrantRepository grants, PasswordHasher hasher, SqlAccessGuard guard) {
    this.users = users;
    this.grants = grants;
    this.hasher = hasher;
    this.guard = guard;
  }

  public List<UserDto> list() {
    requireAdmin();
    return users.findAll().stream().map(UserService::toDto).toList();
  }

  public UserDto create(UserCreateRequest request) {
    requireAdmin();
    String username = request.username().trim();
    users
        .findByUsername(username)
        .ifPresent(
            existing -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "Username already taken: " + username);
            });
    Instant now = Instant.now();
    UserAccount user =
        new UserAccount(
            UUID.randomUUID().toString(),
            username,
            hasher.hash(request.password()),
            Boolean.TRUE.equals(request.admin()),
            request.enabled() == null || request.enabled(),
            now,
            now);
    try {
      users.insert(user);
    } catch (DuplicateKeyException e) {
      // lost a race against a concurrent create; the unique index is the source of truth
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken: " + username);
    }
    return toDto(user);
  }

  /**
   * {@code synchronized} serializes the last-enabled-admin check and the write within this JVM; a
   * multi-instance deployment would need a database-level guard instead.
   */
  public synchronized UserDto update(String id, UserUpdateRequest request) {
    requireAdmin();
    UserAccount existing = find(id);
    boolean admin = request.admin() == null ? existing.admin() : request.admin();
    boolean enabled = request.enabled() == null ? existing.enabled() : request.enabled();
    if (id.equals(guard.current().userId()) && (!admin || !enabled)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "You cannot demote or disable your own account");
    }
    if (existing.admin()
        && existing.enabled()
        && !(admin && enabled)
        && countEnabledAdmins() <= 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot demote or disable the last enabled admin");
    }
    UserAccount updated =
        new UserAccount(
            existing.id(),
            existing.username(),
            existing.passwordHash(),
            admin,
            enabled,
            existing.createdTime(),
            Instant.now());
    users.update(updated);
    return toDto(updated);
  }

  public void resetPassword(String id, PasswordResetRequest request) {
    requireAdmin();
    find(id);
    users.updatePasswordHash(id, hasher.hash(request.password()), Instant.now());
  }

  /** Synchronized for the same reason as {@link #update}. */
  public synchronized void delete(String id) {
    requireAdmin();
    UserAccount existing = find(id);
    if (id.equals(guard.current().userId())) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "You cannot delete your own account");
    }
    if (existing.admin() && existing.enabled() && countEnabledAdmins() <= 1) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Cannot delete the last enabled admin");
    }
    grants.deleteByUser(id);
    users.deleteById(id);
  }

  private long countEnabledAdmins() {
    return users.findAll().stream().filter(u -> u.admin() && u.enabled()).count();
  }

  private UserAccount find(String id) {
    return users
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + id));
  }

  private void requireAdmin() {
    if (!guard.current().admin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
  }

  private static UserDto toDto(UserAccount user) {
    return new UserDto(
        user.id(),
        user.username(),
        user.admin(),
        user.enabled(),
        user.createdTime(),
        user.updatedTime());
  }
}
