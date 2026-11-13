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

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IdGeneratorTest {

  @Test
  void generatesUniqueAndTimeOrderedIds() {
    Set<Long> ids = new HashSet<>();
    long previous = -1;
    // far more than one millisecond's worth of sequence (1024) to force the wait-for-next-ms path
    for (int i = 0; i < 50_000; i++) {
      long id = IdGenerator.next();
      assertThat(ids.add(id)).as("duplicate id %d at iteration %d", id, i).isTrue();
      assertThat(id).isGreaterThan(previous);
      previous = id;
    }
  }
}
