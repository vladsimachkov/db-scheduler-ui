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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.sql.service.SqlJobService;
import no.bekk.dbscheduler.ui.users.dto.SchemaRequest;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.model.SqlSchema;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.GrantRepository;
import no.bekk.dbscheduler.ui.users.repo.SchemaRepository;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SchemaServiceTest {

  private final SchemaRepository schemas = mock(SchemaRepository.class);
  private final GrantRepository grants = mock(GrantRepository.class);
  private final UserRepository users = mock(UserRepository.class);
  private final SqlConnectionRepository connections = mock(SqlConnectionRepository.class);
  private final SqlJobService jobs = mock(SqlJobService.class);

  private SchemaService service(boolean admin, Set<String> granted) {
    SqlAccessGuard guard =
        new SqlAccessGuard() {
          @Override
          public AuthPrincipal current() {
            return new AuthPrincipal("me", "me", admin);
          }

          @Override
          public boolean isAdmin() {
            return admin;
          }

          @Override
          public boolean canAccess(String schemaId) {
            return admin || (schemaId != null && granted.contains(schemaId));
          }

          @Override
          public void require(String schemaId) {
            if (!canAccess(schemaId)) {
              throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found");
            }
          }

          @Override
          public Set<String> accessibleSchemaIds() {
            return admin ? null : granted;
          }
        };
    return new SchemaService(schemas, grants, users, connections, jobs, guard);
  }

  private static SqlSchema schema(String id, String name) {
    Instant now = Instant.now();
    return new SqlSchema(id, name, null, now, now);
  }

  @Test
  void listVisibleShowsAllToAdminAndOnlyGrantedToOthers() {
    when(schemas.findAll()).thenReturn(List.of(schema("s1", "One"), schema("s2", "Two")));

    assertThat(service(true, Set.of()).listVisible()).extracting("id").containsExactly("s1", "s2");
    assertThat(service(false, Set.of("s1")).listVisible()).extracting("id").containsExactly("s1");
  }

  @Test
  void createRejectsNonAdminAndDuplicateName() {
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(false, Set.of("s1")).create(new SchemaRequest("New", null)))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    verify(schemas, never()).insert(any());

    when(schemas.findByName("one")).thenReturn(Optional.of(schema("s1", "one")));
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(true, Set.of()).create(new SchemaRequest(" one ", null)))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);
    verify(schemas, never()).insert(any());
  }

  @Test
  void createTrimsNameAndInserts() {
    service(true, Set.of()).create(new SchemaRequest("  New  ", " desc "));

    var captor = org.mockito.ArgumentCaptor.forClass(SqlSchema.class);
    verify(schemas).insert(captor.capture());
    assertThat(captor.getValue().name()).isEqualTo("New");
    assertThat(captor.getValue().description()).isEqualTo("desc");
  }

  @Test
  void deleteRefusesWhileConnectionsOrJobsRemain() {
    when(schemas.findById("s1")).thenReturn(Optional.of(schema("s1", "One")));
    when(connections.countBySchemaId("s1")).thenReturn(2L);

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(true, Set.of()).delete("s1"))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);

    when(connections.countBySchemaId("s1")).thenReturn(0L);
    when(jobs.anyJobInSchema("s1")).thenReturn(true);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(true, Set.of()).delete("s1"))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);
    verify(schemas, never()).deleteById("s1");
  }

  @Test
  void deleteEmptySchemaRemovesGrantsAndRow() {
    when(schemas.findById("s1")).thenReturn(Optional.of(schema("s1", "One")));
    when(connections.countBySchemaId("s1")).thenReturn(0L);
    when(jobs.anyJobInSchema("s1")).thenReturn(false);

    service(true, Set.of()).delete("s1");

    verify(grants).deleteBySchema("s1");
    verify(schemas).deleteById("s1");
  }

  @Test
  void grantResolvesUsernameAndIsIdempotent() {
    when(schemas.findById("s1")).thenReturn(Optional.of(schema("s1", "One")));
    when(users.findByUsername("bob"))
        .thenReturn(
            Optional.of(
                new UserAccount("u2", "bob", "hash", false, true, Instant.now(), Instant.now())));

    service(true, Set.of()).grant("s1", "bob");
    verify(grants).grant("s1", "u2");

    when(grants.exists("s1", "u2")).thenReturn(true);
    service(true, Set.of()).grant("s1", "bob");
    verify(grants, org.mockito.Mockito.times(1)).grant("s1", "u2");
  }

  @Test
  void grantUnknownUserAnswers404AndNonAdmin403() {
    when(schemas.findById("s1")).thenReturn(Optional.of(schema("s1", "One")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(true, Set.of()).grant("s1", "ghost"))
        .matches(e -> e.getStatusCode() == HttpStatus.NOT_FOUND);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(false, Set.of("s1")).grant("s1", "bob"))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
  }

  @Test
  void everyAdminOnlyOperationRejectsNonAdmin() {
    SchemaService bob = service(false, Set.of("s1"));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.update("s1", new SchemaRequest("x", null)))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.delete("s1"))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.listGrants("s1"))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> bob.revoke("s1", "bob"))
        .matches(e -> e.getStatusCode() == HttpStatus.FORBIDDEN);
    verify(schemas, never()).update(any());
    verify(schemas, never()).deleteById(any());
  }

  @Test
  void updateRejectsNameTakenByAnotherSchemaButAllowsOwnName() {
    when(schemas.findById("s1")).thenReturn(Optional.of(schema("s1", "One")));
    when(schemas.findByName("Two")).thenReturn(Optional.of(schema("s2", "Two")));

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> service(true, Set.of()).update("s1", new SchemaRequest("Two", null)))
        .matches(e -> e.getStatusCode() == HttpStatus.CONFLICT);
    verify(schemas, never()).update(any());

    // renaming to its own (possibly trimmed) name is not a conflict with itself
    when(schemas.findByName("One")).thenReturn(Optional.of(schema("s1", "One")));
    service(true, Set.of()).update("s1", new SchemaRequest(" One ", "d"));
    verify(schemas).update(any());
  }

  @Test
  void revokeUnknownUserIsANoOp() {
    when(schemas.findById("s1")).thenReturn(Optional.of(schema("s1", "One")));

    service(true, Set.of()).revoke("s1", "ghost");

    verify(grants, never()).revoke(any(), any());
  }
}
