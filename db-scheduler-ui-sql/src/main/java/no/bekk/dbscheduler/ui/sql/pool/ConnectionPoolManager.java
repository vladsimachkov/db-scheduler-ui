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
package no.bekk.dbscheduler.ui.sql.pool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import no.bekk.dbscheduler.ui.sql.crypto.CredentialCipher;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Lazily creates one small Hikari pool per registered target-database connection, keyed by
 * connection id. Pools are evicted when a connection is updated or deleted.
 */
public class ConnectionPoolManager implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(ConnectionPoolManager.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();
  private final CredentialCipher cipher;

  public ConnectionPoolManager(CredentialCipher cipher) {
    this.cipher = cipher;
  }

  public HikariDataSource poolFor(SqlJobConnection connection) {
    return pools.computeIfAbsent(connection.id(), id -> createPool(connection));
  }

  /** Close and drop the pool for a connection (after the connection was updated or deleted). */
  public void evict(String connectionId) {
    HikariDataSource removed = pools.remove(connectionId);
    if (removed != null) {
      removed.close();
    }
  }

  @Override
  public void destroy() {
    pools.values().forEach(HikariDataSource::close);
    pools.clear();
  }

  private HikariDataSource createPool(SqlJobConnection connection) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(connection.jdbcUrl());
    config.setUsername(connection.username());
    config.setPassword(cipher.decrypt(connection.password()));
    if (connection.driverClass() != null && !connection.driverClass().isBlank()) {
      loadDriver(connection.driverClass());
      config.setDriverClassName(connection.driverClass());
    }
    config.setMaximumPoolSize(2);
    config.setMinimumIdle(0);
    config.setIdleTimeout(60_000);
    config.setConnectionTimeout(10_000);
    config.setPoolName("sql-job-" + connection.id());
    applyProperties(connection.propertiesJson(), config);
    log.info("Creating connection pool for {} ({})", connection.name(), connection.jdbcUrl());
    return new HikariDataSource(config);
  }

  private void loadDriver(String driverClass) {
    try {
      Class.forName(driverClass);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException(
          "JDBC driver class '"
              + driverClass
              + "' is not on the classpath. Add the driver jar (ojdbc11, trino-jdbc, hive-jdbc, ...)"
              + " to the application classpath.",
          e);
    }
  }

  private void applyProperties(String propertiesJson, HikariConfig config) {
    if (propertiesJson == null || propertiesJson.isBlank()) {
      return;
    }
    Map<String, Object> properties;
    try {
      properties = JSON.readValue(propertiesJson, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Connection properties_json is not a valid JSON object", e);
    }
    properties.forEach((key, value) -> config.addDataSourceProperty(key, value));
  }
}
