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
package com.github.bekk.exampleapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Functional flow against a real second database engine: SQLite (file-backed, test-scope driver —
 * the BYO-driver model in action). Creates a connection, tests it, creates a cron job that fires
 * every second, then waits for db-scheduler to actually run it on schedule (no run-now) and checks
 * the execution record plus the side effect inside the SQLite file.
 */
@org.springframework.boot.test.context.SpringBootTest(
    classes = {ExampleApp.class},
    webEnvironment =
        org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "db-scheduler.polling-interval=1s",
      "spring.datasource.url=jdbc:h2:mem:sqlitejobsdb;DB_CLOSE_DELAY=-1",
    })
@EnableAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
class SqlJobsSqliteFunctionalTest {

  private static final String API = "/db-scheduler-api";

  @LocalServerPort private Integer serverPort;
  @Autowired private TestRestTemplate restTemplate;

  @TempDir private File tempDir;

  private String baseUrl;
  private String sqliteUrl;

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + serverPort;
    // file-based on purpose: in-memory SQLite is per-connection and would not survive the pool
    sqliteUrl = "jdbc:sqlite:" + new File(tempDir, "target.db").getAbsolutePath();
  }

  @Test
  void scheduledJobFiresAgainstSqlite() throws Exception {
    String connectionId = null;
    String jobId = null;
    try {
      // --- create connection ---
      ResponseEntity<JsonNode> createdConnection =
          restTemplate.postForEntity(
              baseUrl + API + "/sql-connections",
              new HashMap<>(
                  Map.of("name", "sqlite-target", "dialect", "GENERIC", "jdbcUrl", sqliteUrl)),
              JsonNode.class);
      assertThat(createdConnection.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      connectionId = createdConnection.getBody().get("id").asText();

      // --- test connection: the test-scope sqlite driver answers ---
      JsonNode testResult =
          restTemplate
              .postForEntity(
                  baseUrl + API + "/sql-connections/" + connectionId + "/test",
                  null,
                  JsonNode.class)
              .getBody();
      assertThat(testResult.get("success").asBoolean())
          .as("test-connection: %s", testResult)
          .isTrue();
      assertThat(testResult.get("productName").asText()).isEqualTo("SQLite");

      // --- create a job that fires every second ---
      ResponseEntity<JsonNode> createdJob =
          restTemplate.postForEntity(
              baseUrl + API + "/sql-jobs",
              new HashMap<>(
                  Map.of(
                      "name",
                      "sqlite-probe",
                      "connectionId",
                      connectionId,
                      "scripts",
                      List.of(
                          "create table if not exists probe(id integer);",
                          "insert into probe values (1);"),
                      "cronExpression",
                      "* * * * * *",
                      "timeoutSeconds",
                      30)),
              JsonNode.class);
      assertThat(createdJob.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      jobId = createdJob.getBody().get("jobId").asText();
      assertThat(createdJob.getBody().get("nextRunTime").isNull()).isFalse();

      // --- wait for a scheduled fire (polling-interval is 1s; 30s is generous headroom) ---
      String executionsPath = "/sql-jobs/" + jobId + "/executions";
      await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(500))
          .until(() -> getJson(executionsPath).size() > 0);

      JsonNode execution = getJson("/sql-jobs/" + jobId + "/executions").get(0);
      assertThat(execution.get("succeeded").asBoolean())
          .as("execution failed: %s", execution.get("errorMessage").asText(""))
          .isTrue();
      assertThat(execution.get("statementsExecuted").asInt()).isEqualTo(2);
      assertThat(execution.get("triggeredBy").asText()).isEqualTo("SCHEDULE");
      String scriptText = execution.get("scriptText").asText();
      assertThat(scriptText).contains("create table").contains("insert into probe");

      // --- the side effect really landed in the SQLite file ---
      try (Connection sqlite = DriverManager.getConnection(sqliteUrl);
          Statement statement = sqlite.createStatement();
          ResultSet rs = statement.executeQuery("select count(*) from probe")) {
        assertThat(rs.next()).isTrue();
        assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(1);
      }
    } finally {
      deleteQuietly("/sql-jobs/" + jobId, jobId);
      deleteQuietly("/sql-connections/" + connectionId, connectionId);
    }
  }

  private JsonNode getJson(String path) {
    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity(baseUrl + API + path, JsonNode.class);
    assertThat(response.getStatusCode())
        .as("GET %s -> %s", path, response.getBody())
        .isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  /**
   * A cron-per-second job may be mid-execution exactly when we delete it (409); retry until it is
   * gone. Null id (creation failed) is a no-op.
   */
  private void deleteQuietly(String path, String id) {
    if (id == null) {
      return;
    }
    for (int attempt = 0; attempt < 20; attempt++) {
      int status =
          restTemplate
              .exchange(baseUrl + API + path, HttpMethod.DELETE, null, JsonNode.class)
              .getStatusCode()
              .value();
      if (status == HttpStatus.OK.value() || status == HttpStatus.NOT_FOUND.value()) {
        return;
      }
      try {
        Thread.sleep(300);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
    }
  }
}
