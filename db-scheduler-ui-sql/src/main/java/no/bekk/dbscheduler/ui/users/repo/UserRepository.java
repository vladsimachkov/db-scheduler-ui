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
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;

/** Stores UI user accounts in {@code sql_users}. */
public class UserRepository {

  private static final String COLUMNS =
      "id, username, password_hash, admin, enabled, created_time, updated_time";

  private final JdbcTemplate jdbc;

  public UserRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void insert(UserAccount user) {
    jdbc.update(
        "insert into sql_users (" + COLUMNS + ") values (?,?,?,?,?,?,?)",
        user.id(),
        user.username(),
        user.passwordHash(),
        user.admin(),
        user.enabled(),
        Timestamp.from(user.createdTime()),
        Timestamp.from(user.updatedTime()));
  }

  public Optional<UserAccount> findById(String id) {
    return jdbc.query("select " + COLUMNS + " from sql_users where id = ?", this::map, id).stream()
        .findFirst();
  }

  public Optional<UserAccount> findByUsername(String username) {
    return jdbc
        .query("select " + COLUMNS + " from sql_users where username = ?", this::map, username)
        .stream()
        .findFirst();
  }

  public List<UserAccount> findAll() {
    return jdbc.query("select " + COLUMNS + " from sql_users order by username", this::map);
  }

  /**
   * Updates the mutable flags (admin/enabled) — never the password, see {@link
   * #updatePasswordHash}.
   */
  public int update(UserAccount user) {
    return jdbc.update(
        "update sql_users set admin=?, enabled=?, updated_time=? where id=?",
        user.admin(),
        user.enabled(),
        Timestamp.from(user.updatedTime()),
        user.id());
  }

  public int updatePasswordHash(String id, String passwordHash, Instant when) {
    return jdbc.update(
        "update sql_users set password_hash=?, updated_time=? where id=?",
        passwordHash,
        Timestamp.from(when),
        id);
  }

  public int deleteById(String id) {
    return jdbc.update("delete from sql_users where id = ?", id);
  }

  public long count() {
    Long count = jdbc.queryForObject("select count(*) from sql_users", Long.class);
    return count == null ? 0 : count;
  }

  private UserAccount map(ResultSet rs, int rowNum) throws SQLException {
    return new UserAccount(
        rs.getString("id"),
        rs.getString("username"),
        rs.getString("password_hash"),
        rs.getBoolean("admin"),
        rs.getBoolean("enabled"),
        toInstant(rs.getTimestamp("created_time")),
        toInstant(rs.getTimestamp("updated_time")));
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
