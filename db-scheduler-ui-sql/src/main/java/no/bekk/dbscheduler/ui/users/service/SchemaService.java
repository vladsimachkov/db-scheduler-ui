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
import java.util.Set;
import java.util.UUID;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.sql.service.SqlJobService;
import no.bekk.dbscheduler.ui.users.dto.SchemaDto;
import no.bekk.dbscheduler.ui.users.dto.SchemaRequest;
import no.bekk.dbscheduler.ui.users.model.SqlSchema;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.GrantRepository;
import no.bekk.dbscheduler.ui.users.repo.SchemaRepository;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Schema administration. Reads are visible-scope (a granted user sees their own schemas); every
 * mutation and the grant panel are global-admin only — 403, not 404, because the existence of the
 * admin API is not itself a secret.
 */
public class SchemaService {

  private final SchemaRepository schemas;
  private final GrantRepository grants;
  private final UserRepository users;
  private final SqlConnectionRepository connections;
  private final SqlJobService jobs;
  private final SqlAccessGuard guard;

  public SchemaService(
      SchemaRepository schemas,
      GrantRepository grants,
      UserRepository users,
      SqlConnectionRepository connections,
      SqlJobService jobs,
      SqlAccessGuard guard) {
    this.schemas = schemas;
    this.grants = grants;
    this.users = users;
    this.connections = connections;
    this.jobs = jobs;
    this.guard = guard;
  }

  /** Schemas the current user can see: all for an admin, granted ones otherwise. */
  public List<SchemaDto> listVisible() {
    Set<String> visible = guard.accessibleSchemaIds();
    return schemas.findAll().stream()
        .filter(s -> visible == null || visible.contains(s.id()))
        .map(SchemaService::toDto)
        .toList();
  }

  public SchemaDto create(SchemaRequest request) {
    requireAdmin();
    String name = request.name().trim();
    schemas
        .findByName(name)
        .ifPresent(
            existing -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "Schema name already taken: " + name);
            });
    Instant now = Instant.now();
    SqlSchema schema =
        new SqlSchema(
            UUID.randomUUID().toString(), name, blankToNull(request.description()), now, now);
    try {
      schemas.insert(schema);
    } catch (DuplicateKeyException e) {
      // lost a race against a concurrent create; the unique index is the source of truth
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Schema name already taken: " + name);
    }
    return toDto(schema);
  }

  public SchemaDto update(String id, SchemaRequest request) {
    requireAdmin();
    SqlSchema existing = find(id);
    String name = request.name().trim();
    schemas
        .findByName(name)
        .filter(other -> !other.id().equals(id))
        .ifPresent(
            other -> {
              throw new ResponseStatusException(
                  HttpStatus.CONFLICT, "Schema name already taken: " + name);
            });
    SqlSchema updated =
        new SqlSchema(
            id, name, blankToNull(request.description()), existing.createdTime(), Instant.now());
    schemas.update(updated);
    return toDto(updated);
  }

  /** A schema may only be deleted once empty: no connections, no jobs. Grants go with it. */
  public void delete(String id) {
    requireAdmin();
    find(id);
    long connectionCount = connections.countBySchemaId(id);
    if (connectionCount > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Schema still contains " + connectionCount + " connection(s)");
    }
    if (jobs.anyJobInSchema(id)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Schema still contains jobs");
    }
    grants.deleteBySchema(id);
    schemas.deleteById(id);
  }

  public List<String> listGrants(String schemaId) {
    requireAdmin();
    find(schemaId);
    return grants.listUsernamesBySchema(schemaId);
  }

  /** Idempotent: granting an existing grant succeeds without duplicating the row. */
  public void grant(String schemaId, String username) {
    requireAdmin();
    find(schemaId);
    String userId = findUserByUsername(username).id();
    if (!grants.exists(schemaId, userId)) {
      try {
        grants.grant(schemaId, userId);
      } catch (DuplicateKeyException e) {
        // lost a race against a concurrent grant; the row exists, which is what the caller wanted
      }
    }
  }

  /** Revoking a grant that does not exist is a no-op. */
  public void revoke(String schemaId, String username) {
    requireAdmin();
    find(schemaId);
    users.findByUsername(username.trim()).ifPresent(user -> grants.revoke(schemaId, user.id()));
  }

  private UserAccount findUserByUsername(String username) {
    return users
        .findByUsername(username.trim())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + username));
  }

  private SqlSchema find(String id) {
    return schemas
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schema not found: " + id));
  }

  private void requireAdmin() {
    if (!guard.current().admin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static SchemaDto toDto(SqlSchema schema) {
    return new SchemaDto(
        schema.id(),
        schema.name(),
        schema.description(),
        schema.createdTime(),
        schema.updatedTime());
  }
}
