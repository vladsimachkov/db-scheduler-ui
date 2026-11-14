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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import no.bekk.dbscheduler.ui.sql.crypto.CredentialCipher;
import no.bekk.dbscheduler.ui.sql.dto.SqlConnectionRequest;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.pool.ConnectionPoolManager;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Schema-grant enforcement on the connection registry: hidden connections answer 404. */
class SqlConnectionServiceAccessTest {

  private final SqlConnectionRepository repository = mock(SqlConnectionRepository.class);
  private final ConnectionPoolManager pools = mock(ConnectionPoolManager.class);
  private final CredentialCipher cipher = mock(CredentialCipher.class);

  @SuppressWarnings("unchecked")
  private final SchedulerClient client = mock(SchedulerClient.class);

  private SqlConnectionService service(SqlAccessGuard guard) {
    return new SqlConnectionService(repository, pools, cipher, client, guard);
  }

  private static SqlAccessGuard guard(boolean admin, String... granted) {
    Set<String> grantedSchemas = Set.of(granted);
    return new SqlAccessGuard() {
      @Override
      public AuthPrincipal current() {
        return new AuthPrincipal("u", "u", admin);
      }

      @Override
      public boolean isAdmin() {
        return admin;
      }

      @Override
      public boolean canAccess(String schemaId) {
        return admin || (schemaId != null && grantedSchemas.contains(schemaId));
      }

      @Override
      public void require(String schemaId) {
        if (!canAccess(schemaId)) {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
        }
      }

      @Override
      public Set<String> accessibleSchemaIds() {
        return admin ? null : grantedSchemas;
      }
    };
  }

  private static SqlJobConnection connection(String id, String schemaId) {
    Instant now = Instant.now();
    return new SqlJobConnection(
        id,
        "c-" + id,
        schemaId,
        SqlDialect.GENERIC,
        "jdbc:h2:mem:x",
        null,
        "sa",
        null,
        null,
        now,
        now);
  }

  private static SqlConnectionRequest request(String schemaId) {
    return new SqlConnectionRequest(
        "name", schemaId, SqlDialect.GENERIC, "jdbc:h2:mem:x", null, "sa", null, null, null);
  }

  @Test
  void listHidesSchemasTheUserWasNotGranted() {
    when(repository.findAll())
        .thenReturn(
            List.of(connection("c1", "s1"), connection("c2", "s2"), connection("c3", null)));

    assertThat(service(guard(false, "s1")).list()).extracting("id").containsExactly("c1");
    assertThat(service(guard(true)).list()).extracting("id").containsExactly("c1", "c2", "c3");
    // regression: no guard bean (users feature off) = everything visible, as before
    assertThat(service(null).list()).extracting("id").containsExactly("c1", "c2", "c3");
  }

  @Test
  void getHiddenConnectionAnswers404() {
    when(repository.findById("c2")).thenReturn(Optional.of(connection("c2", "s2")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).get("c2"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
  }

  @Test
  void createWithoutSchemaIsRejectedForNonAdmin() {
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).create(request(null)))
        .matches(e -> e.getStatusCode() == HttpStatus.BAD_REQUEST)
        .withMessageContaining("A schema is required");
    verify(repository, never()).insert(any());
  }

  @Test
  void createInUngrantedSchemaAnswers404() {
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).create(request("s2")))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(repository, never()).insert(any());
  }

  @Test
  void createInGrantedSchemaInsertsTheAssignment() {
    service(guard(false, "s1")).create(request("s1"));

    ArgumentCaptor<SqlJobConnection> inserted = ArgumentCaptor.forClass(SqlJobConnection.class);
    verify(repository).insert(inserted.capture());
    assertThat(inserted.getValue().schemaId()).isEqualTo("s1");
  }

  @Test
  void updateMovingToUngrantedSchemaAnswers404() {
    when(repository.findById("c1")).thenReturn(Optional.of(connection("c1", "s1")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).update("c1", request("s2")))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(repository, never()).update(any());
  }

  @Test
  void updateWithoutSchemaKeepsTheCurrentAssignment() {
    when(repository.findById("c1")).thenReturn(Optional.of(connection("c1", "s1")));

    service(guard(false, "s1")).update("c1", request(null));

    ArgumentCaptor<SqlJobConnection> updated = ArgumentCaptor.forClass(SqlJobConnection.class);
    verify(repository).update(updated.capture());
    assertThat(updated.getValue().schemaId()).isEqualTo("s1");
  }

  @Test
  void deleteHiddenConnectionAnswers404AndKeepsIt() {
    when(repository.findById("c2")).thenReturn(Optional.of(connection("c2", "s2")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).delete("c2"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(repository, never()).deleteById("c2");
  }

  @Test
  void testHiddenConnectionAnswers404() {
    when(repository.findById("c2")).thenReturn(Optional.of(connection("c2", "s2")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(guard(false, "s1")).test("c2"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    verify(pools, never()).poolFor(any());
  }
}
