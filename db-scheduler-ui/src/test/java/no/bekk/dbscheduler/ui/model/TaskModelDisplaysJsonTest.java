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
package no.bekk.dbscheduler.ui.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire shape of {@link TaskModel#displays}: without a resolver the field must not appear
 * at all (Jackson includes nulls by default), so old clients see the pre-feature payload.
 */
class TaskModelDisplaysJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void nullDisplaysIsOmittedWhileOtherNullsAreStillWritten() throws Exception {
    TaskModel model = new TaskModel();
    model.setTaskName("t");

    String json = objectMapper.writeValueAsString(model);

    assertThat(json).doesNotContain("\"displays\"");
    assertThat(json).contains("\"pickedBy\":null");
  }

  @Test
  void resolvedDisplaysAreSerialized() throws Exception {
    TaskModel model = new TaskModel();
    model.setDisplays(List.of(new TaskDisplay("nightly", "schema-1")));

    String json = objectMapper.writeValueAsString(model);

    assertThat(json)
        .contains("\"displays\":[{\"displayName\":\"nightly\",\"schemaId\":\"schema-1\"}]");
  }
}
