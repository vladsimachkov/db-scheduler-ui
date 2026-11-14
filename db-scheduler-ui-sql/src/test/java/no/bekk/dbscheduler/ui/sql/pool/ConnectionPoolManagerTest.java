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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.sql.SQLException;
import no.bekk.dbscheduler.ui.sql.crypto.CredentialCipher;
import no.bekk.dbscheduler.ui.sql.model.SqlDialect;
import no.bekk.dbscheduler.ui.sql.model.SqlJobConnection;
import org.junit.jupiter.api.Test;

class ConnectionPoolManagerTest {

  private final ConnectionPoolManager pools = new ConnectionPoolManager(new CredentialCipher(null));

  private static SqlJobConnection h2(String id) {
    return new SqlJobConnection(
        id,
        "test",
        null,
        SqlDialect.GENERIC,
        "jdbc:h2:mem:pool_" + id + ";DB_CLOSE_DELAY=-1",
        null,
        "sa",
        "",
        null,
        null,
        null);
  }

  @Test
  void createsWorkingPoolAndReusesIt() throws SQLException {
    var pool = pools.poolFor(h2("reuse"));
    try (var connection = pool.getConnection()) {
      assertThat(connection.isValid(1)).isTrue();
    }
    assertThat(pools.poolFor(h2("reuse"))).isSameAs(pool);
    pools.destroy();
  }

  @Test
  void evictReplacesPool() {
    var first = pools.poolFor(h2("evict"));
    pools.evict("evict");
    var second = pools.poolFor(h2("evict"));
    assertThat(second).isNotSameAs(first);
    pools.destroy();
  }

  @Test
  void unknownDriverClassGivesHelpfulError() {
    SqlJobConnection bad =
        new SqlJobConnection(
            "bad",
            "bad",
            null,
            SqlDialect.IMPALA,
            "jdbc:impala://host:10000",
            "com.cloudera.impala.jdbc41.Driver",
            null,
            null,
            null,
            null,
            null);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> pools.poolFor(bad))
        .withMessageContaining("not on the classpath");
  }

  @Test
  void invalidPropertiesJsonRejected() {
    SqlJobConnection bad =
        new SqlJobConnection(
            "badjson",
            "bad",
            null,
            SqlDialect.GENERIC,
            "jdbc:h2:mem:badjson",
            null,
            "sa",
            "",
            "not json",
            null,
            null);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> pools.poolFor(bad))
        .withMessageContaining("JSON");
  }
}
