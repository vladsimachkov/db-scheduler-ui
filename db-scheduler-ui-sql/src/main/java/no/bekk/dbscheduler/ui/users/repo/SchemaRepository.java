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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import no.bekk.dbscheduler.ui.users.model.SqlSchema;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stores schema groups (of jobs and connections) in {@code sql_schemas}. */
public class SchemaRepository {

  private static final String COLUMNS = "id, name, description, created_time, updated_time";

  private final JdbcTemplate jdbc;

  public SchemaRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(SqlSchema schema) {
    jdbc.update(
        "insert into sql_schemas (" + COLUMNS + ") values (?,?,?,?,?)",
        schema.id(),
        schema.name(),
        schema.description(),
        Timestamp.from(schema.createdTime()),
        Timestamp.from(schema.updatedTime()));
  }

  public Optional<SqlSchema> findById(String id) {
    return jdbc
        .query("select " + COLUMNS + " from sql_schemas where id = ?", this::map, id)
        .stream()
        .findFirst();
  }

  public Optional<SqlSchema> findByName(String name) {
    return jdbc
        .query("select " + COLUMNS + " from sql_schemas where name = ?", this::map, name)
        .stream()
        .findFirst();
  }

  public List<SqlSchema> findAll() {
    return jdbc.query("select " + COLUMNS + " from sql_schemas order by name", this::map);
  }

  public int update(SqlSchema schema) {
    return jdbc.update(
        "update sql_schemas set name=?, description=?, updated_time=? where id=?",
        schema.name(),
        schema.description(),
        Timestamp.from(schema.updatedTime()),
        schema.id());
  }

  public int deleteById(String id) {
    return jdbc.update("delete from sql_schemas where id = ?", id);
  }

  public long count() {
    Long count = jdbc.queryForObject("select count(*) from sql_schemas", Long.class);
    return count == null ? 0 : count;
  }

  private SqlSchema map(ResultSet rs, int rowNum) throws SQLException {
    return new SqlSchema(
        rs.getString("id"),
        rs.getString("name"),
        rs.getString("description"),
        toInstant(rs.getTimestamp("created_time")),
        toInstant(rs.getTimestamp("updated_time")));
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
