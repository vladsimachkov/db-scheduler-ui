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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kagkarlsson.scheduler.ScheduledExecutionsFilter;
import com.github.kagkarlsson.scheduler.SchedulerClient;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import no.bekk.dbscheduler.ui.sql.crypto.CredentialCipher;
import no.bekk.dbscheduler.ui.sql.dto.ConnectionTestResult;
import no.bekk.dbscheduler.ui.sql.dto.SqlConnectionDto;
import no.bekk.dbscheduler.ui.sql.dto.SqlConnectionRequest;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.pool.ConnectionPoolManager;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** CRUD and connectivity tests for registered target-database connections. */
public class SqlConnectionService {

  private static final Logger log = LoggerFactory.getLogger(SqlConnectionService.class);

  private final SqlConnectionRepository repository;
  private final ConnectionPoolManager pools;
  private final CredentialCipher cipher;
  private final SchedulerClient client;

  /** {@code null} when the users feature is off — then every connection is visible, as before. */
  private final SqlAccessGuard guard;

  public SqlConnectionService(
      SqlConnectionRepository repository,
      ConnectionPoolManager pools,
      CredentialCipher cipher,
      SchedulerClient client) {
    this(repository, pools, cipher, client, null);
  }

  public SqlConnectionService(
      SqlConnectionRepository repository,
      ConnectionPoolManager pools,
      CredentialCipher cipher,
      SchedulerClient client,
      SqlAccessGuard guard) {
    this.repository = repository;
    this.pools = pools;
    this.cipher = cipher;
    this.client = client;
    this.guard = guard;
  }

  public List<SqlConnectionDto> list() {
    // null means "all schemas" (admin or users feature off); unassigned connections stay
    // admin-only because a granted set never contains null
    Set<String> visibleSchemas = guard == null ? null : guard.accessibleSchemaIds();
    return repository.findAll().stream()
        // null-safe: a granted set is an arbitrary Set and may reject null (e.g. Set.of)
        .filter(
            c ->
                visibleSchemas == null
                    || (c.schemaId() != null && visibleSchemas.contains(c.schemaId())))
        .map(SqlConnectionService::toDto)
        .collect(Collectors.toList());
  }

  public SqlConnectionDto get(String id) {
    return toDto(findVisible(id));
  }

  public SqlConnectionDto create(SqlConnectionRequest request) {
    validatePropertiesJson(request.propertiesJson());
    Instant now = Instant.now();
    String schemaId = blankToNull(request.schemaId());
    if (guard != null && !guard.isAdmin()) {
      if (schemaId == null) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A schema is required");
      }
      guard.require(schemaId);
    }
    SqlJobConnection connection =
        new SqlJobConnection(
            UUID.randomUUID().toString(),
            request.name(),
            schemaId,
            request.dialect(),
            request.jdbcUrl(),
            blankToNull(request.driverClass()),
            request.username(),
            blankToNull(request.password()) == null ? null : cipher.encrypt(request.password()),
            blankToNull(request.propertiesJson()),
            now,
            now);
    repository.insert(connection);
    return toDto(connection);
  }

  public SqlConnectionDto update(String id, SqlConnectionRequest request) {
    validatePropertiesJson(request.propertiesJson());
    SqlJobConnection existing = findVisible(id);
    String effectiveSchema =
        request.schemaId() == null || request.schemaId().isBlank()
            ? existing.schemaId()
            : request.schemaId().trim();
    if (guard != null
        && !guard.isAdmin()
        && !Objects.equals(effectiveSchema, existing.schemaId())) {
      // moving into another schema requires a grant on the target as well
      guard.require(effectiveSchema);
    }
    String password;
    if (Boolean.TRUE.equals(request.clearPassword())) {
      password = null;
    } else if (request.password() == null || request.password().isBlank()) {
      password = existing.password(); // keep stored password
    } else {
      password = cipher.encrypt(request.password());
    }
    SqlJobConnection updated =
        new SqlJobConnection(
            existing.id(),
            request.name(),
            // a request that omits schemaId keeps the current assignment (no silent unassignment)
            effectiveSchema,
            request.dialect(),
            request.jdbcUrl(),
            blankToNull(request.driverClass()),
            request.username(),
            password,
            blankToNull(request.propertiesJson()),
            existing.createdTime(),
            Instant.now());
    repository.update(updated);
    pools.evict(id);
    return toDto(updated);
  }

  public void delete(String id) {
    findVisible(id);
    boolean inUse = referencedByJobs(id);
    if (inUse) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Connection is still referenced by SQL jobs: " + id);
    }
    repository.deleteById(id);
    pools.evict(id);
  }

  public ConnectionTestResult test(String id) {
    SqlJobConnection connection = findVisible(id);
    try {
      DataSource pool = pools.poolFor(connection);
      try (Connection jdbc = pool.getConnection()) {
        DatabaseMetaData metaData = jdbc.getMetaData();
        return ConnectionTestResult.ok(
            metaData.getDatabaseProductName(), metaData.getDatabaseProductVersion());
      }
    } catch (Exception e) {
      log.warn("Connection test failed for {}", connection.name(), e);
      // Evict so a half-broken pool is not reused after a failed test
      pools.evict(id);
      return ConnectionTestResult.failed(rootMessage(e));
    }
  }

  private boolean referencedByJobs(String connectionId) {
    return referencesJobs(SqlJobService.SQL_SCRIPT_TASK, connectionId)
        || referencesJobs(SqlJobService.SQL_SCRIPT_ONCE_TASK, connectionId);
  }

  private boolean referencesJobs(String taskName, String connectionId) {
    return client
        .getScheduledExecutionsForTask(taskName, SqlJobData.class, ScheduledExecutionsFilter.all())
        .stream()
        .anyMatch(e -> connectionId.equals(e.getData().getConnectionId()));
  }

  private SqlJobConnection find(String id) {
    return repository
        .findById(id)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found: " + id));
  }

  /** Like {@link #find}, but a connection in a schema the caller cannot see answers as 404. */
  private SqlJobConnection findVisible(String id) {
    SqlJobConnection connection = find(id);
    if (guard != null && !guard.canAccess(connection.schemaId())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Connection not found: " + id);
    }
    return connection;
  }

  private static void validatePropertiesJson(String propertiesJson) {
    if (propertiesJson == null || propertiesJson.isBlank()) {
      return;
    }
    try {
      new ObjectMapper().readTree(propertiesJson);
    } catch (Exception e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "properties_json must be a valid JSON object");
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static SqlConnectionDto toDto(SqlJobConnection connection) {
    return new SqlConnectionDto(
        connection.id(),
        connection.name(),
        connection.schemaId(),
        connection.dialect(),
        connection.jdbcUrl(),
        connection.driverClass(),
        connection.username(),
        connection.password() != null && !connection.password().isBlank(),
        connection.propertiesJson(),
        connection.createdTime(),
        connection.updatedTime());
  }

  private static String rootMessage(Throwable t) {
    Throwable current = t;
    while (current.getCause() != null && current.getMessage() == null) {
      current = current.getCause();
    }
    return current.getMessage() != null ? current.getMessage() : current.toString();
  }
}
