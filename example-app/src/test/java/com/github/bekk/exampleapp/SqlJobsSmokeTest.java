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

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * End-to-end SQL job flow against the app's own H2 database standing in as the target: create
 * connection, test it, create a cron job, run it now, verify execution history and side effects,
 * then clean up.
 */
@SpringBootTest(
    classes = {ExampleApp.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "db-scheduler.polling-interval=1s",
      // own database: the 1s poller would otherwise eat one-time tasks other
      // test contexts scheduled on the shared in-memory testdb
      "spring.datasource.url=jdbc:h2:mem:sqljobsdb;DB_CLOSE_DELAY=-1",
    })
@EnableAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqlJobsSmokeTest {

  private static final String API = "/db-scheduler-api";
  private static String baseUrl;

  @LocalServerPort private Integer serverPort;
  @Autowired private TestRestTemplate restTemplate;
  @Autowired private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    baseUrl = "http://localhost:" + serverPort;
  }

  @Test
  @Order(1)
  void fullSqlJobLifecycle() throws InterruptedException {
    // --- connection ---
    Map<String, Object> connectionRequest =
        new HashMap<>(
            Map.of(
                "name",
                "Local H2",
                "dialect",
                "GENERIC",
                "jdbcUrl",
                "jdbc:h2:mem:sqljobsdb",
                "username",
                "sa",
                "password",
                ""));
    ResponseEntity<JsonNode> createdConnection =
        restTemplate.postForEntity(
            baseUrl + API + "/sql-connections", connectionRequest, JsonNode.class);
    assertThat(createdConnection.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String connectionId = createdConnection.getBody().get("id").asText();
    assertThat(createdConnection.getBody().get("passwordSet").asBoolean()).isFalse();

    JsonNode testResult =
        restTemplate
            .postForEntity(
                baseUrl + API + "/sql-connections/" + connectionId + "/test", null, JsonNode.class)
            .getBody();
    assertThat(testResult.get("success").asBoolean()).isTrue();
    assertThat(testResult.get("productName").asText()).isEqualTo("H2");

    // --- job ---
    Map<String, Object> jobRequest =
        new HashMap<>(
            Map.of(
                "name",
                "smoke-job",
                "connectionId",
                connectionId,
                "scripts",
                List.of(
                    "CREATE TABLE IF NOT EXISTS smoke_output(x INT); INSERT INTO smoke_output VALUES (42)"),
                "cronExpression",
                "0 0/5 * * * *",
                "timeoutSeconds",
                30));
    ResponseEntity<JsonNode> createdJob =
        restTemplate.postForEntity(baseUrl + API + "/sql-jobs", jobRequest, JsonNode.class);
    assertThat(createdJob.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String jobId = createdJob.getBody().get("jobId").asText();
    assertThat(createdJob.getBody().get("recurring").asBoolean()).isTrue();
    assertThat(createdJob.getBody().get("nextRunTime").isNull()).isFalse();

    // regression: the dashboard's main list must serialize task data without a cycle
    assertThat(
            restTemplate
                .getForEntity(
                    baseUrl
                        + "/db-scheduler-api/tasks/all?filter=ALL&pageNumber=0&size=10"
                        + "&sorting=DEFAULT&asc=true&searchTerm=",
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // listed with connection name resolved
    JsonNode jobs = getJson("/sql-jobs");
    assertThat(jobs).anyMatch(j -> j.get("jobId").asText().equals(jobId));
    assertThat(jobs)
        .anyMatch(
            j ->
                j.get("jobId").asText().equals(jobId)
                    && "Local H2".equals(j.get("connectionName").asText()));

    // invalid cron rejected
    Map<String, Object> badRequest = new HashMap<>(jobRequest);
    badRequest.put("cronExpression", "not a cron");
    assertThat(
            restTemplate
                .postForEntity(baseUrl + API + "/sql-jobs", badRequest, JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // --- run now and wait for execution ---
    assertThat(
            restTemplate
                .postForEntity(
                    baseUrl + API + "/sql-jobs/" + jobId + "/run-now", null, JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    JsonNode executions = awaitExecutions(jobId);
    JsonNode execution = executions.get(0);
    assertThat(execution.get("succeeded").asBoolean()).isTrue();
    assertThat(execution.get("statementsExecuted").asInt()).isEqualTo(2);
    assertThat(execution.get("rowsAffected").asLong()).isGreaterThanOrEqualTo(1);
    assertThat(execution.get("triggeredBy").asText()).isEqualTo("SCHEDULE");

    // the script really ran against the target database
    assertThat(jdbc.queryForObject("select count(*) from smoke_output", Integer.class))
        .isEqualTo(1);

    // --- cleanup ---
    restTemplate.delete(baseUrl + API + "/sql-jobs/" + jobId);
    assertThat(
            restTemplate
                .getForEntity(baseUrl + API + "/sql-jobs/" + jobId, JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(
            restTemplate
                .exchange(
                    baseUrl + API + "/sql-connections/" + connectionId,
                    org.springframework.http.HttpMethod.DELETE,
                    null,
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  @Order(2)
  void connectionInUseCannotBeDeleted() {
    Map<String, Object> connectionRequest =
        new HashMap<>(
            Map.of(
                "name",
                "Guarded",
                "dialect",
                "GENERIC",
                "jdbcUrl",
                "jdbc:h2:mem:sqljobsdb",
                "username",
                "sa"));
    String connectionId =
        restTemplate
            .postForEntity(baseUrl + API + "/sql-connections", connectionRequest, JsonNode.class)
            .getBody()
            .get("id")
            .asText();

    Map<String, Object> jobRequest =
        new HashMap<>(
            Map.of(
                "name",
                "guard-job",
                "connectionId",
                connectionId,
                "scripts",
                List.of("SELECT 1"),
                "cronExpression",
                "0 0/5 * * * *"));
    String jobId =
        restTemplate
            .postForEntity(baseUrl + API + "/sql-jobs", jobRequest, JsonNode.class)
            .getBody()
            .get("jobId")
            .asText();

    ResponseEntity<JsonNode> deleteConnection =
        restTemplate.exchange(
            baseUrl + API + "/sql-connections/" + connectionId,
            org.springframework.http.HttpMethod.DELETE,
            null,
            JsonNode.class);
    assertThat(deleteConnection.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    restTemplate.delete(baseUrl + API + "/sql-jobs/" + jobId);
    restTemplate.delete(baseUrl + API + "/sql-connections/" + connectionId);
  }

  @Test
  @Order(3)
  void runningJobStaysVisibleAndRejectsMutation() throws InterruptedException {
    Map<String, Object> connectionRequest =
        new HashMap<>(
            Map.of(
                "name",
                "Slow",
                "dialect",
                "GENERIC",
                "jdbcUrl",
                "jdbc:h2:mem:sqljobsdb",
                "username",
                "sa"));
    String connectionId =
        restTemplate
            .postForEntity(baseUrl + API + "/sql-connections", connectionRequest, JsonNode.class)
            .getBody()
            .get("id")
            .asText();

    // recursive CTE that runs until the 3s query timeout cancels it
    Map<String, Object> jobRequest =
        new HashMap<>(
            Map.of(
                "name",
                "slow-job",
                "connectionId",
                connectionId,
                "scripts",
                List.of(
                    "WITH recursive r(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM r WHERE n <"
                        + " 100000000) SELECT count(*) FROM r"),
                "cronExpression",
                "0 0/5 * * * *",
                "timeoutSeconds",
                3));
    String jobId =
        restTemplate
            .postForEntity(baseUrl + API + "/sql-jobs", jobRequest, JsonNode.class)
            .getBody()
            .get("jobId")
            .asText();
    restTemplate.postForEntity(
        baseUrl + API + "/sql-jobs/" + jobId + "/run-now", null, JsonNode.class);

    // wait until the scheduler picks it up
    boolean running = false;
    for (int i = 0; i < 30 && !running; i++) {
      running = getJson("/sql-jobs/" + jobId).get("running").asBoolean();
      if (!running) {
        Thread.sleep(300);
      }
    }
    assertThat(running).as("job should be visible as running").isTrue();

    // while picked, reschedule/cancel must be rejected with 409, not 500
    assertThat(
            restTemplate
                .postForEntity(
                    baseUrl + API + "/sql-jobs/" + jobId + "/run-now", null, JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            restTemplate
                .exchange(
                    baseUrl + API + "/sql-jobs/" + jobId,
                    org.springframework.http.HttpMethod.DELETE,
                    null,
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);

    // after the timeout cancels the statement, the job is deletable again
    awaitExecutions(jobId);
    assertThat(
            restTemplate
                .exchange(
                    baseUrl + API + "/sql-jobs/" + jobId,
                    org.springframework.http.HttpMethod.DELETE,
                    null,
                    JsonNode.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    restTemplate.delete(baseUrl + API + "/sql-connections/" + connectionId);
  }

  private JsonNode getJson(String path) {
    ResponseEntity<JsonNode> response =
        restTemplate.getForEntity(baseUrl + API + path, JsonNode.class);
    assertThat(response.getStatusCode())
        .as("GET %s -> %s", path, response.getBody())
        .isEqualTo(HttpStatus.OK);
    return response.getBody();
  }

  private JsonNode awaitExecutions(String jobId) throws InterruptedException {
    // polling-interval is 1s in test properties; allow generous headroom
    for (int attempt = 0; attempt < 60; attempt++) {
      JsonNode executions = getJson("/sql-jobs/" + jobId + "/executions");
      if (executions.size() > 0) {
        return executions;
      }
      Thread.sleep(500);
    }
    throw new AssertionError("No execution recorded for job " + jobId + " within 30s");
  }
}
