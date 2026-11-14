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

import java.util.Optional;
import no.bekk.dbscheduler.ui.users.crypto.PasswordHasher;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;

/** Verifies UI user credentials against the stored PBKDF2 hashes. */
public class AuthService {

  private final UserRepository users;
  private final PasswordHasher hasher;

  /**
   * Well-formed but unused hash, burned on unknown usernames so responses leak no timing info.
   * Built with the configured hasher so the dummy and real paths run the same iteration count — a
   * cheaper dummy would turn unknown-username responses into a user-enumeration oracle.
   */
  private final String dummyHash;

  public AuthService(UserRepository users, PasswordHasher hasher) {
    this.users = users;
    this.hasher = hasher;
    this.dummyHash = hasher.hash("timing-equalizer");
  }

  public Optional<AuthPrincipal> authenticate(String username, String rawPassword) {
    if (username == null || rawPassword == null) {
      return Optional.empty();
    }
    Optional<UserAccount> account = users.findByUsername(username);
    if (account.isEmpty()) {
      hasher.matches(rawPassword, dummyHash);
      return Optional.empty();
    }
    UserAccount user = account.get();
    if (!user.enabled() || !hasher.matches(rawPassword, user.passwordHash())) {
      return Optional.empty();
    }
    return Optional.of(new AuthPrincipal(user.id(), user.username(), user.admin()));
  }
}
