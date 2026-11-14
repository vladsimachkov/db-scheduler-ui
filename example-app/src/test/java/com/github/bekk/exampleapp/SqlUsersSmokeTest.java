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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end walk of the built-in users feature: anonymous callers are rejected, the bootstrap
 * admin logs in and wires up two schemas, a user and a grant; the granted user sees exactly the
 * granted schema, cross-schema writes are refused, revocation hides everything again, and logout
 * ends the session.
 *
 * <p>Cookies are handled by hand — TestRestTemplate does not keep a session across calls.
 */
@SpringBootTest(
    classes = {ExampleApp.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:usersdb;DB_CLOSE_DELAY=-1",
      "db-scheduler.polling-interval=1s",
      "db-scheduler-ui.users.enabled=true",
      "db-scheduler-ui.users.bootstrap-username=admin",
      "db-scheduler-ui.users.bootstrap-password=bootstrap-pw",
    })
@EnableAutoConfiguration(
    exclude = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class})
class SqlUsersSmokeTest {

  private static final String API = "/db-scheduler-api";
  private static final Pattern ID = Pattern.compile("\"(?:id|jobId)\"\\s*:\\s*\"([^\"]+)\"");

  @LocalServerPort private Integer serverPort;
  @Autowired private TestRestTemplate restTemplate;

  private String api;

  @BeforeEach
  void setUp() {
    api = "http://localhost:" + serverPort + API;
  }

  /** A mutation with a JSON body; the CSRF header is what the filter demands. */
  private static HttpEntity<String> mutation(String body, String session) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.set("X-Requested-With", "XMLHttpRequest");
    if (session != null) {
      headers.set(HttpHeaders.COOKIE, session);
    }
    return new HttpEntity<>(body, headers);
  }

  private static HttpEntity<String> mutation(String body) {
    return mutation(body, null);
  }

  private static HttpEntity<Void> get(String session) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.COOKIE, session);
    return new HttpEntity<>(headers);
  }

  private String login(String username, String password) {
    ResponseEntity<String> response =
        restTemplate.exchange(
            api + "/auth/login",
            HttpMethod.POST,
            mutation("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"),
            String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).isNotNull();
    return setCookie.split(";")[0];
  }

  private String post(String path, String body, String session) {
    return restTemplate
        .exchange(api + path, HttpMethod.POST, mutation(body, session), String.class)
        .getBody();
  }

  private static String extractId(String body) {
    Matcher matcher = ID.matcher(body);
    assertThat(matcher.find()).as("id present in %s", body).isTrue();
    return matcher.group(1);
  }

  @Test
  void loginGrantRevokeLifecycle() {
    // --- anonymous: everything guarded except the public few ---
    assertThat(restTemplate.getForEntity(api + "/sql-jobs", String.class).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(restTemplate.getForEntity(api + "/config", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);

    // wrong password is refused before any session exists
    assertThat(
            restTemplate
                .exchange(
                    api + "/auth/login",
                    HttpMethod.POST,
                    mutation("{\"username\":\"admin\",\"password\":\"wrong\"}"),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);

    String admin = login("admin", "bootstrap-pw");
    assertThat(
            restTemplate
                .exchange(api + "/auth/me", HttpMethod.GET, get(admin), String.class)
                .getBody())
        .contains("\"authenticated\":true")
        .contains("\"admin\":true");

    // --- CSRF: a mutation without the header is refused even with a valid session ---
    HttpHeaders noCsrf = new HttpHeaders();
    noCsrf.setContentType(MediaType.APPLICATION_JSON);
    noCsrf.set(HttpHeaders.COOKIE, admin);
    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-schemas",
                    HttpMethod.POST,
                    new HttpEntity<>("{\"name\":\"no-csrf\"}", noCsrf),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    // --- admin wires up: two schemas, one user, one grant ---
    String alpha = extractId(post("/sql-schemas", "{\"name\":\"alpha\"}", admin));
    String beta = extractId(post("/sql-schemas", "{\"name\":\"beta\"}", admin));
    post("/users", "{\"username\":\"bob\",\"password\":\"bob-pw-123\"}", admin);
    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-schemas/" + alpha + "/grants/bob",
                    HttpMethod.PUT,
                    mutation(null, admin),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    // a connection in beta, so bob can be tempted to cross the border
    String betaConnection =
        extractId(
            post(
                "/sql-connections",
                "{\"name\":\"beta-conn\",\"schemaId\":\""
                    + beta
                    + "\",\"dialect\":\"GENERIC\",\"jdbcUrl\":\"jdbc:h2:mem:usersdb\"}",
                admin));

    String bob = login("bob", "bob-pw-123");

    // bob sees exactly one schema
    assertThat(
            restTemplate
                .exchange(api + "/sql-schemas", HttpMethod.GET, get(bob), String.class)
                .getBody())
        .contains("alpha")
        .doesNotContain("beta");

    // bob works inside alpha: connection, then a job on that connection
    String connection =
        extractId(
            post(
                "/sql-connections",
                "{\"name\":\"bob-conn\",\"schemaId\":\""
                    + alpha
                    + "\",\"dialect\":\"GENERIC\",\"jdbcUrl\":\"jdbc:h2:mem:usersdb\"}",
                bob));
    String job =
        extractId(
            post(
                "/sql-jobs",
                "{\"name\":\"bob-job\",\"schemaId\":\""
                    + alpha
                    + "\",\"connectionId\":\""
                    + connection
                    + "\",\"scripts\":[\"SELECT 1\"],\"cronExpression\":\"0 0/5 * * * *\"}",
                bob));

    // cross-schema job: alpha's job may not sit on beta's connection — and beta is invisible,
    // so the backend refuses with a plain "unknown connection" 400 rather than leaking it
    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-jobs",
                    HttpMethod.POST,
                    mutation(
                        "{\"name\":\"sneak\",\"schemaId\":\""
                            + alpha
                            + "\",\"connectionId\":\""
                            + betaConnection
                            + "\",\"scripts\":[\"SELECT 1\"],\"cronExpression\":\"0 0/5 * * * *\"}",
                        bob),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);

    // bob cannot even create inside the ungranted schema — it does not exist for him
    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-connections",
                    HttpMethod.POST,
                    mutation(
                        "{\"name\":\"sneak\",\"schemaId\":\""
                            + beta
                            + "\",\"dialect\":\"GENERIC\",\"jdbcUrl\":\"jdbc:h2:mem:usersdb\"}",
                        bob),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    // user administration is admin-only
    assertThat(
            restTemplate
                .exchange(api + "/users", HttpMethod.GET, get(bob), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.FORBIDDEN);

    // --- revoke: alpha vanishes from bob's world ---
    assertThat(
            restTemplate
                .exchange(
                    api + "/sql-schemas/" + alpha + "/grants/bob",
                    HttpMethod.DELETE,
                    mutation(null, admin),
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .exchange(api + "/sql-schemas", HttpMethod.GET, get(bob), String.class)
                .getBody())
        .isEqualTo("[]");
    assertThat(
            restTemplate
                .exchange(api + "/sql-jobs/" + job, HttpMethod.GET, get(bob), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NOT_FOUND);

    // --- logout ends the session ---
    assertThat(
            restTemplate
                .exchange(api + "/auth/logout", HttpMethod.POST, mutation(null, bob), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            restTemplate
                .exchange(api + "/sql-jobs", HttpMethod.GET, get(bob), String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
  }
}
