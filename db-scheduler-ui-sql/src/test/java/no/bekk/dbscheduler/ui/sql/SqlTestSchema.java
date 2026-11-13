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
package no.bekk.dbscheduler.ui.sql;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Test helper: creates the sql-jobs schema (from the shipped H2 DDL) on an in-memory store. */
public final class SqlTestSchema {

  private SqlTestSchema() {}

  /** A DataSource that never closes — shared by the repositories under test. */
  public static DataSource inMemoryStore(String name) {
    return new SingleConnectionDataSource(
        "jdbc:h2:mem:" + name + ";DB_CLOSE_DELAY=-1", "sa", "", true);
  }

  public static void applyH2Ddl(DataSource dataSource) {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Path ddl =
        Path.of(System.getProperty("user.dir")).resolve("../sql/sql-jobs/h2.sql").normalize();
    String script;
    try {
      script = Files.readString(ddl, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    for (String statement : script.split(";")) {
      if (!statement.isBlank()) {
        jdbc.execute(statement);
      }
    }
  }
}
