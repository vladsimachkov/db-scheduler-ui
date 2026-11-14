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
package no.bekk.dbscheduler.ui.users.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import no.bekk.dbscheduler.ui.sql.SqlTestSchema;
import no.bekk.dbscheduler.ui.users.model.SqlSchema;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

class UsersRepositoriesTest {

  // Unique db name per test instance: DB_CLOSE_DELAY=-1 keeps named in-memory dbs alive in the
  // shared JVM, so a fixed name would leak rows between tests.
  private final JdbcTemplate jdbc = newJdbc("users_repo_" + UUID.randomUUID());
  private final UserRepository users = new UserRepository(jdbc);
  private final SchemaRepository schemas = new SchemaRepository(jdbc);
  private final GrantRepository grants = new GrantRepository(jdbc);

  private static JdbcTemplate newJdbc(String name) {
    var store = SqlTestSchema.inMemoryStore(name);
    SqlTestSchema.applyH2Ddl(store);
    return new JdbcTemplate(store);
  }

  // Millisecond truncation: the JDBC timestamp round-trip drops sub-milli precision (see
  // RepositoriesTest).
  private static UserAccount user(String username) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    return new UserAccount(
        UUID.randomUUID().toString(),
        username,
        "pbkdf2:1:c2Fsdg==:aGFzaA==",
        false,
        true,
        now,
        now);
  }

  private static SqlSchema schema(String name) {
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    return new SqlSchema(UUID.randomUUID().toString(), name, "desc", now, now);
  }

  private UserAccount insertUser(UserAccount account) {
    users.insert(account);
    return account;
  }

  private SqlSchema insertSchema(SqlSchema schema) {
    schemas.insert(schema);
    return schema;
  }

  @Test
  void userRoundTrip() {
    UserAccount created = user("alice");
    users.insert(created);

    assertThat(users.findById(created.id())).contains(created);
    assertThat(users.findByUsername("alice")).contains(created);
    assertThat(users.count()).isEqualTo(1);
  }

  @Test
  void uniqueUsernameIsRejected() {
    users.insert(user("alice"));
    assertThatExceptionOfType(DuplicateKeyException.class)
        .isThrownBy(() -> users.insert(user("alice")));
  }

  @Test
  void updateFlagsAndPassword() {
    UserAccount created = user("bob");
    users.insert(created);

    UserAccount changed =
        new UserAccount(
            created.id(),
            created.username(),
            created.passwordHash(),
            true,
            false,
            created.createdTime(),
            Instant.now());
    assertThat(users.update(changed)).isEqualTo(1);
    UserAccount reloaded = users.findById(created.id()).orElseThrow();
    assertThat(reloaded.admin()).isTrue();
    assertThat(reloaded.enabled()).isFalse();

    users.updatePasswordHash(created.id(), "pbkdf2:2:c2Fsdg==:aGFzaA==", Instant.now());
    assertThat(users.findById(created.id()).orElseThrow().passwordHash())
        .isEqualTo("pbkdf2:2:c2Fsdg==:aGFzaA==");
  }

  @Test
  void deleteUser() {
    UserAccount created = user("carol");
    users.insert(created);
    assertThat(users.deleteById(created.id())).isEqualTo(1);
    assertThat(users.findByUsername("carol")).isEmpty();
    assertThat(users.deleteById(created.id())).isZero();
  }

  @Test
  void schemaRoundTripAndUniqueName() {
    SqlSchema created = schema("billing");
    schemas.insert(created);

    assertThat(schemas.findById(created.id())).contains(created);
    assertThat(schemas.findByName("billing")).contains(created);
    assertThat(schemas.count()).isEqualTo(1);
    assertThatExceptionOfType(DuplicateKeyException.class)
        .isThrownBy(() -> schemas.insert(schema("billing")));
  }

  @Test
  void updateSchema() {
    SqlSchema created = schema("legacy");
    schemas.insert(created);
    SqlSchema changed =
        new SqlSchema(created.id(), "renamed", "new desc", created.createdTime(), Instant.now());
    assertThat(schemas.update(changed)).isEqualTo(1);
    assertThat(schemas.findById(created.id()).orElseThrow().name()).isEqualTo("renamed");
  }

  @Test
  void grantRevokeAndQueries() {
    UserAccount alice = insertUser(user("alice"));
    UserAccount bob = insertUser(user("bob"));
    SqlSchema billing = insertSchema(schema("billing"));

    grants.grant(billing.id(), alice.id());
    assertThat(grants.exists(billing.id(), alice.id())).isTrue();
    assertThat(grants.exists(billing.id(), bob.id())).isFalse();
    assertThat(grants.listSchemaIdsByUser(alice.id())).containsExactly(billing.id());
    assertThat(grants.listUsernamesBySchema(billing.id())).containsExactly("alice");

    assertThat(grants.revoke(billing.id(), alice.id())).isEqualTo(1);
    assertThat(grants.exists(billing.id(), alice.id())).isFalse();
    assertThat(grants.revoke(billing.id(), alice.id())).isZero();
  }

  @Test
  void bulkDeleteBySchemaAndUser() {
    UserAccount alice = insertUser(user("alice"));
    SqlSchema billing = insertSchema(schema("billing"));
    SqlSchema ops = insertSchema(schema("ops"));
    grants.grant(billing.id(), alice.id());
    grants.grant(ops.id(), alice.id());

    assertThat(grants.deleteBySchema(billing.id())).isEqualTo(1);
    assertThat(grants.listSchemaIdsByUser(alice.id())).containsExactly(ops.id());

    assertThat(grants.deleteByUser(alice.id())).isEqualTo(1);
    assertThat(grants.listSchemaIdsByUser(alice.id())).isEmpty();
  }
}
