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

import java.util.ArrayList;
import java.util.List;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;

/**
 * Splits a SQL script into individual statements on {@code ;}, ignoring separators inside string
 * literals, quoted identifiers and line/block comments. Dialect quirks:
 *
 * <ul>
 *   <li>{@code MYSQL}: {@code #} starts a line comment and backslash escapes inside strings ({@code
 *       \'}) are honored.
 *   <li>{@code IMPALA} (Hive SQL): backslash escapes inside strings are honored.
 * </ul>
 *
 * <p>Known limitations: Oracle q-quotes ({@code q'[...]'}) containing quotes or semicolons, and
 * PL/SQL blocks ({@code BEGIN ... END;}) with internal semicolons cannot be split reliably. Prefix
 * such a script with the marker {@code --@no-split} on the first line to send it as one statement.
 */
public final class SqlScriptSplitter {

  public static final String NO_SPLIT_MARKER = "--@no-split";

  private SqlScriptSplitter() {}

  public static List<String> split(String script, SqlDialect dialect) {
    if (script == null || script.isBlank()) {
      return List.of();
    }
    if (script.stripLeading().startsWith(NO_SPLIT_MARKER)) {
      String stripped = script.stripLeading().substring(NO_SPLIT_MARKER.length()).strip();
      return stripped.isEmpty() ? List.of() : List.of(stripped);
    }

    List<String> statements = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int i = 0;
    int len = script.length();

    while (i < len) {
      char c = script.charAt(i);
      char next = i + 1 < len ? script.charAt(i + 1) : '\0';

      if (c == '-' && next == '-') {
        i = copyLineComment(script, i, current);
      } else if (c == '#' && dialect == SqlDialect.MYSQL) {
        i = copyLineComment(script, i, current);
      } else if (c == '/' && next == '*') {
        int end = script.indexOf("*/", i + 2);
        if (end == -1) {
          current.append(script, i, len);
          i = len;
        } else {
          current.append(script, i, end + 2);
          i = end + 2;
        }
      } else if (c == '\'' || c == '"') {
        i = copyQuoted(script, i, dialect, current);
      } else if (c == ';') {
        addStatement(statements, current, dialect);
        current.setLength(0);
        i++;
      } else {
        current.append(c);
        i++;
      }
    }
    addStatement(statements, current, dialect);
    return statements;
  }

  private static int copyLineComment(String script, int from, StringBuilder out) {
    int end = script.indexOf('\n', from);
    if (end == -1) {
      out.append(script, from, script.length());
      return script.length();
    }
    out.append(script, from, end + 1);
    return end + 1;
  }

  private static int copyQuoted(String script, int from, SqlDialect dialect, StringBuilder out) {
    boolean backslashEscapes = dialect == SqlDialect.MYSQL || dialect == SqlDialect.IMPALA;
    char quote = script.charAt(from);
    out.append(quote);
    int i = from + 1;
    int len = script.length();
    while (i < len) {
      char sc = script.charAt(i);
      if (backslashEscapes && sc == '\\' && i + 1 < len) {
        out.append(sc).append(script.charAt(i + 1));
        i += 2;
        continue;
      }
      out.append(sc);
      i++;
      if (sc == quote) {
        // doubled quote is an escaped quote, keep scanning
        if (i < len && script.charAt(i) == quote) {
          out.append(script.charAt(i));
          i++;
        } else {
          break;
        }
      }
    }
    return i;
  }

  private static void addStatement(
      List<String> statements, StringBuilder candidate, SqlDialect dialect) {
    String statement = candidate.toString().trim();
    if (!statement.isEmpty() && hasSqlCode(statement, dialect)) {
      statements.add(statement);
    }
  }

  /** True if the segment contains anything other than whitespace and comments. */
  private static boolean hasSqlCode(String segment, SqlDialect dialect) {
    int i = 0;
    int len = segment.length();
    while (i < len) {
      char c = segment.charAt(i);
      char next = i + 1 < len ? segment.charAt(i + 1) : '\0';
      if (Character.isWhitespace(c)) {
        i++;
      } else if (c == '-' && next == '-') {
        int end = segment.indexOf('\n', i);
        i = end == -1 ? len : end + 1;
      } else if (c == '#' && dialect == SqlDialect.MYSQL) {
        int end = segment.indexOf('\n', i);
        i = end == -1 ? len : end + 1;
      } else if (c == '/' && next == '*') {
        int end = segment.indexOf("*/", i + 2);
        i = end == -1 ? len : end + 2;
      } else {
        return true;
      }
    }
    return false;
  }
}
