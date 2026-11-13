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
package no.bekk.dbscheduler.ui.sql.repo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stores registered target-database connections in {@code sql_job_connections}. */
public class SqlConnectionRepository {

  private static final String COLUMNS =
      "id, name, dialect, jdbc_url, driver_class, username, password, properties_json, created_time, updated_time";

  private final JdbcTemplate jdbc;

  public SqlConnectionRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(SqlJobConnection connection) {
    jdbc.update(
        "insert into sql_job_connections (" + COLUMNS + ") values (?,?,?,?,?,?,?,?,?,?)",
        connection.id(),
        connection.name(),
        connection.dialect().name(),
        connection.jdbcUrl(),
        connection.driverClass(),
        connection.username(),
        connection.password(),
        connection.propertiesJson(),
        Timestamp.from(connection.createdTime()),
        Timestamp.from(connection.updatedTime()));
  }

  public int update(SqlJobConnection connection) {
    return jdbc.update(
        "update sql_job_connections set name=?, dialect=?, jdbc_url=?, driver_class=?, username=?,"
            + " password=?, properties_json=?, updated_time=? where id=?",
        connection.name(),
        connection.dialect().name(),
        connection.jdbcUrl(),
        connection.driverClass(),
        connection.username(),
        connection.password(),
        connection.propertiesJson(),
        Timestamp.from(connection.updatedTime()),
        connection.id());
  }

  public int deleteById(String id) {
    return jdbc.update("delete from sql_job_connections where id = ?", id);
  }

  public Optional<SqlJobConnection> findById(String id) {
    return jdbc
        .query("select " + COLUMNS + " from sql_job_connections where id = ?", this::map, id)
        .stream()
        .findFirst();
  }

  public List<SqlJobConnection> findAll() {
    return jdbc.query("select " + COLUMNS + " from sql_job_connections order by name", this::map);
  }

  private SqlJobConnection map(ResultSet rs, int rowNum) throws SQLException {
    return new SqlJobConnection(
        rs.getString("id"),
        rs.getString("name"),
        parseDialect(rs.getString("dialect"), rs.getString("id")),
        rs.getString("jdbc_url"),
        rs.getString("driver_class"),
        rs.getString("username"),
        rs.getString("password"),
        rs.getString("properties_json"),
        toInstant(rs.getTimestamp("created_time")),
        toInstant(rs.getTimestamp("updated_time")));
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  /** Unknown enum values in hand-edited rows must not take down the whole list endpoint. */
  private static SqlDialect parseDialect(String value, String connectionId) {
    try {
      return SqlDialect.valueOf(value);
    } catch (IllegalArgumentException e) {
      return SqlDialect.GENERIC;
    }
  }
}
