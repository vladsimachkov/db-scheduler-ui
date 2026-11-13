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
package no.bekk.dbscheduler.ui.sql.exec;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import org.junit.jupiter.api.Test;

class SqlScriptSplitterTest {

  private List<String> split(String script) {
    return SqlScriptSplitter.split(script, SqlDialect.GENERIC);
  }

  @Test
  void splitsOnSemicolon() {
    assertThat(split("SELECT 1; SELECT 2")).containsExactly("SELECT 1", "SELECT 2");
  }

  @Test
  void ignoresTrailingSemicolonAndBlankStatements() {
    assertThat(split("SELECT 1;;SELECT 2;")).containsExactly("SELECT 1", "SELECT 2");
  }

  @Test
  void keepsSemicolonInsideStringLiteral() {
    assertThat(split("INSERT INTO t VALUES ('a;b'); SELECT 2"))
        .containsExactly("INSERT INTO t VALUES ('a;b')", "SELECT 2");
  }

  @Test
  void keepsSemicolonInsideDoubledQuoteEscape() {
    assertThat(split("INSERT INTO t VALUES ('it''s; ok')"))
        .containsExactly("INSERT INTO t VALUES ('it''s; ok')");
  }

  @Test
  void keepsSemicolonInsideQuotedIdentifier() {
    assertThat(split("SELECT \"col;name\" FROM t")).containsExactly("SELECT \"col;name\" FROM t");
  }

  @Test
  void keepsSemicolonInsideLineComment() {
    assertThat(split("-- drop; everything\nSELECT 1;"))
        .containsExactly("-- drop; everything\nSELECT 1");
  }

  @Test
  void keepsSemicolonInsideBlockComment() {
    assertThat(split("/* a; b */ SELECT 1")).containsExactly("/* a; b */ SELECT 1");
  }

  @Test
  void dropsCommentOnlyScript() {
    assertThat(split("-- nothing here")).isEmpty();
  }

  @Test
  void handlesUnterminatedStringWithoutSplittingRest() {
    assertThat(split("SELECT 'unterminated; still one")).hasSize(1);
  }

  @Test
  void noSplitMarkerReturnsWholeScriptAsOneStatement() {
    String script =
        """
        --@no-split
        BEGIN
          EXECUTE IMMEDIATE 'CREATE TABLE t (x INT)';
        END;""";
    List<String> statements = split(script);
    assertThat(statements).hasSize(1);
    assertThat(statements.get(0)).startsWith("BEGIN").doesNotContain("--@no-split");
  }

  @Test
  void mysqlBackslashEscapeKeepsStringIntact() {
    assertThat(
            SqlScriptSplitter.split("INSERT INTO t VALUES ('a\\'b'); SELECT 2", SqlDialect.MYSQL))
        .containsExactly("INSERT INTO t VALUES ('a\\'b')", "SELECT 2");
  }

  @Test
  void mysqlHashLineComment() {
    assertThat(SqlScriptSplitter.split("SELECT 1 # comment; here\n; SELECT 2", SqlDialect.MYSQL))
        .containsExactly("SELECT 1 # comment; here", "SELECT 2");
  }

  @Test
  void hashIsNotACommentOutsideMysql() {
    // for GENERIC dialect '#' is ordinary text, the ';' after it still splits
    assertThat(SqlScriptSplitter.split("SELECT '#' # x; SELECT 2", SqlDialect.GENERIC))
        .containsExactly("SELECT '#' # x", "SELECT 2");
  }

  @Test
  void impalaHonorsBackslashEscapes() {
    assertThat(
            SqlScriptSplitter.split("INSERT INTO t VALUES ('a\\'b'); SELECT 2", SqlDialect.IMPALA))
        .containsExactly("INSERT INTO t VALUES ('a\\'b')", "SELECT 2");
  }

  @Test
  void dropsBlockCommentOnlySegment() {
    assertThat(split("/* just a note */; SELECT 1")).containsExactly("SELECT 1");
  }

  @Test
  void dropsMultiLineLineCommentOnlySegment() {
    assertThat(split("-- one\n-- two\n; SELECT 1")).containsExactly("SELECT 1");
  }

  @Test
  void handlesNullAndBlank() {
    assertThat(split(null)).isEmpty();
    assertThat(split("   ")).isEmpty();
  }
}
