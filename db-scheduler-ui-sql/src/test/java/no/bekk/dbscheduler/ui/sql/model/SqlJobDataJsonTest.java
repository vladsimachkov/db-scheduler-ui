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
package no.bekk.dbscheduler.ui.sql.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * The core UI bean-serializes raw task data into {@code taskData} API responses; a self-referencing
 * {@code getData()} would make /tasks/all fail with a serialization cycle.
 */
class SqlJobDataJsonTest {

  @Test
  void serializesWithoutSelfReferenceCycle() throws Exception {
    SqlJobData data =
        new SqlJobData(
            "job-1", "Nightly load", "desc", "conn-1", "SELECT 1;", null, 60, "0 0 * * * *", "UTC");
    String json = new ObjectMapper().writeValueAsString(data);
    assertThat(json).contains("\"jobId\":\"job-1\"").contains("\"recurring\":true");
    assertThat(json).doesNotContain("\"data\"").doesNotContain("\"schedule\"");
  }
}
