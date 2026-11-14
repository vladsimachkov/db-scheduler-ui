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

import java.time.Instant;
import java.util.UUID;
import no.bekk.dbscheduler.ui.users.config.DbSchedulerUiUsersProperties;
import no.bekk.dbscheduler.ui.users.crypto.PasswordHasher;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;

/**
 * Seeds the first admin from {@code db-scheduler-ui.users.bootstrap-*} when {@code sql_users} is
 * empty — without it, a fresh install would have no way to log in. Runs once per startup; existing
 * users are never touched.
 */
public class BootstrapAdminInitializer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

  private final UserRepository users;
  private final PasswordHasher hasher;
  private final DbSchedulerUiUsersProperties properties;

  public BootstrapAdminInitializer(
      UserRepository users, PasswordHasher hasher, DbSchedulerUiUsersProperties properties) {
    this.users = users;
    this.hasher = hasher;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (users.count() > 0) {
      return;
    }
    String username = properties.bootstrapUsername();
    String password = properties.bootstrapPassword();
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      log.warn(
          "db-scheduler-ui.users is enabled but sql_users is empty and no bootstrap admin is"
              + " configured (db-scheduler-ui.users.bootstrap-username/-password) — nobody can log"
              + " in until the table is seeded.");
      return;
    }
    Instant now = Instant.now();
    try {
      users.insert(
          new UserAccount(
              UUID.randomUUID().toString(),
              username.trim(),
              hasher.hash(password),
              true,
              true,
              now,
              now));
      log.info(
          "Created bootstrap admin user '{}' — change its password after first login", username);
    } catch (DuplicateKeyException race) {
      log.debug("Bootstrap admin already created by a concurrent instance");
    }
  }
}
