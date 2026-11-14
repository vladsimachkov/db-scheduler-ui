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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Task data is Java-serialized into {@code scheduled_tasks.task_data}. New fields must deserialize
 * as {@code null} in old blobs (fixed serialVersionUID) — this pins that contract for {@code
 * schemaId}.
 */
class SqlJobDataSerializationTest {

  private static SqlJobData roundTrip(SqlJobData data) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
      out.writeObject(data);
    }
    try (ObjectInputStream in =
        new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
      return (SqlJobData) in.readObject();
    }
  }

  @Test
  void schemaIdSurvivesRoundTrip() throws Exception {
    SqlJobData data =
        new SqlJobData(
            "job-1",
            "Nightly load",
            "desc",
            "conn-1",
            null,
            List.of("SELECT 1;"),
            60,
            "0 0 * * * *",
            null,
            "schema-1");
    SqlJobData restored = roundTrip(data);
    assertThat(restored.getSchemaId()).isEqualTo("schema-1");
    assertThat(restored.getJobId()).isEqualTo("job-1");
    assertThat(restored.getScripts()).containsExactly("SELECT 1;");
  }

  @Test
  void legacyConstructorDeserializesWithNullSchemaId() throws Exception {
    // the 9-arg constructor is what pre-users blobs were built with
    SqlJobData legacy =
        new SqlJobData(
            "job-2", "Old job", "desc", "conn-1", "SELECT 1;", null, 60, "0 0 * * * *", "UTC");
    SqlJobData restored = roundTrip(legacy);
    assertThat(restored.getSchemaId()).isNull();
    assertThat(restored.getScript()).isEqualTo("SELECT 1;");
  }
}
