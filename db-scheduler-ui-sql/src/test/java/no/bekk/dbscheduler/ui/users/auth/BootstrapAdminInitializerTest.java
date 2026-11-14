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

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import no.bekk.dbscheduler.ui.sql.SqlTestSchema;
import no.bekk.dbscheduler.ui.users.config.DbSchedulerUiUsersProperties;
import no.bekk.dbscheduler.ui.users.crypto.PasswordHasher;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BootstrapAdminInitializerTest {

  private final DataSource store = SqlTestSchema.inMemoryStore("boot_" + UUID.randomUUID());
  private final UserRepository users = new UserRepository(new JdbcTemplate(store));
  private final PasswordHasher hasher = new PasswordHasher(1000);

  {
    SqlTestSchema.applyH2Ddl(store);
  }

  private BootstrapAdminInitializer initializer(String username, String password) {
    return new BootstrapAdminInitializer(
        users, hasher, new DbSchedulerUiUsersProperties(true, username, password));
  }

  @Test
  void createsAdminWhenTableEmpty() {
    initializer("admin", "bootstrap-pw").run(null);

    UserAccount admin = users.findByUsername("admin").orElseThrow();
    assertThat(admin.admin()).isTrue();
    assertThat(admin.enabled()).isTrue();
    assertThat(hasher.matches("bootstrap-pw", admin.passwordHash())).isTrue();
  }

  @Test
  void skipsWhenUsersAlreadyExist() {
    Instant now = Instant.now();
    users.insert(
        new UserAccount("u1", "existing", "pbkdf2:1:c2Fsdg==:aGFzaA==", false, true, now, now));

    initializer("admin", "pw").run(null);

    assertThat(users.count()).isEqualTo(1);
    assertThat(users.findByUsername("admin")).isEmpty();
  }

  @Test
  void blankBootstrapPropertiesCreateNothing() {
    initializer("", "").run(null);
    assertThat(users.count()).isZero();
  }

  @Test
  void idempotentAcrossRuns() {
    initializer("admin", "pw").run(null);
    initializer("admin", "other-pw").run(null); // second startup must not touch the account
    assertThat(users.count()).isEqualTo(1);
  }
}
