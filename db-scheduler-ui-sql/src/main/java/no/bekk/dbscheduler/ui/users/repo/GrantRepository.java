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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The (user × schema) access grants in {@code sql_schema_grants}. Access is binary: a row grants
 * the user full control over everything inside the schema.
 */
public class GrantRepository {

  private final JdbcTemplate jdbc;

  public GrantRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** Inserts a grant; callers should {@link #exists} first if idempotency matters. */
  public void grant(String schemaId, String userId) {
    jdbc.update(
        "insert into sql_schema_grants (schema_id, user_id) values (?, ?)", schemaId, userId);
  }

  public int revoke(String schemaId, String userId) {
    return jdbc.update(
        "delete from sql_schema_grants where schema_id = ? and user_id = ?", schemaId, userId);
  }

  public boolean exists(String schemaId, String userId) {
    Long count =
        jdbc.queryForObject(
            "select count(*) from sql_schema_grants where schema_id = ? and user_id = ?",
            Long.class,
            schemaId,
            userId);
    return count != null && count > 0;
  }

  public Set<String> listSchemaIdsByUser(String userId) {
    return new HashSet<>(
        jdbc.queryForList(
            "select schema_id from sql_schema_grants where user_id = ?", String.class, userId));
  }

  public List<String> listUsernamesBySchema(String schemaId) {
    return jdbc.queryForList(
        "select u.username from sql_schema_grants g"
            + " join sql_users u on u.id = g.user_id"
            + " where g.schema_id = ? order by u.username",
        String.class,
        schemaId);
  }

  public int deleteBySchema(String schemaId) {
    return jdbc.update("delete from sql_schema_grants where schema_id = ?", schemaId);
  }

  public int deleteByUser(String userId) {
    return jdbc.update("delete from sql_schema_grants where user_id = ?", userId);
  }
}
