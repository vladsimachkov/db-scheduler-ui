package com.github.bekk.exampleapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Verifies the SQL-job module boots and serves its API on Spring Boot 4. */
@AutoConfigureTestRestTemplate
@SpringBootTest(
    classes = {ExampleApp.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:boot4sqljobsdb;DB_CLOSE_DELAY=-1",
      "db-scheduler.polling-interval=1s"
    })
class SqlJobsSmokeTest {

  private static final Pattern ID = Pattern.compile("\"(?:id|jobId)\"\\s*:\\s*\"([^\"]+)\"");

  @LocalServerPort private Integer serverPort;

  private String api;

  @Autowired private ApplicationContext context;

  @Autowired private TestRestTemplate restTemplate;

  @BeforeEach
  void setUp() {
    api = "http://localhost:" + serverPort + "/db-scheduler-api";
  }

  private static HttpEntity<String> json(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  private static String extractId(String body) {
    Matcher matcher = ID.matcher(body);
    assertThat(matcher.find()).as("id present in %s", body).isTrue();
    return matcher.group(1);
  }

  @Test
  void testContextLoads() {
    assertThat(context.containsBean("sqlJobService")).isTrue();
    assertThat(context.containsBean("sqlConnectionService")).isTrue();
  }

  @Test
  void configExposesSqlEnabled() {
    ResponseEntity<String> result = restTemplate.getForEntity(api + "/config", String.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).contains("\"showSql\":true");
  }

  @Test
  void fullSqlJobRoundTrip() {
    ResponseEntity<String> connection =
        restTemplate.postForEntity(
            api + "/sql-connections",
            json(
                "{\"name\":\"boot4\",\"dialect\":\"GENERIC\","
                    + "\"jdbcUrl\":\"jdbc:h2:mem:boot4target;DB_CLOSE_DELAY=-1\",\"username\":\"sa\"}"),
            String.class);
    assertThat(connection.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String connectionId = extractId(connection.getBody());

    ResponseEntity<String> test =
        restTemplate.postForEntity(
            api + "/sql-connections/" + connectionId + "/test", null, String.class);
    assertThat(test.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(test.getBody()).contains("H2");

    ResponseEntity<String> job =
        restTemplate.postForEntity(
            api + "/sql-jobs",
            json(
                "{\"name\":\"boot4-job\",\"connectionId\":\""
                    + connectionId
                    + "\",\"scripts\":[\"SELECT 1;\"],\"cronExpression\":\"0 0 4 * * ?\"}"),
            String.class);
    assertThat(job.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String jobId = extractId(job.getBody());

    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-jobs/" + jobId + "/run-now",
                    HttpMethod.POST,
                    HttpEntity.EMPTY,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // The execution row is written by the scheduler thread — poll briefly.
    String executions = "";
    for (int attempt = 0; attempt < 20; attempt++) {
      executions =
          restTemplate
              .getForEntity(api + "/sql-jobs/" + jobId + "/executions?limit=5", String.class)
              .getBody();
      if (executions != null && executions.contains("\"succeeded\"")) {
        break;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    assertThat(executions).contains("\"succeeded\":true");

    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-jobs/" + jobId, HttpMethod.DELETE, HttpEntity.EMPTY, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-connections/" + connectionId,
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }
}
