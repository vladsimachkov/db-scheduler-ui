package com.github.bekk.exampleapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.config.Customizer.withDefaults;

import com.github.bekk.exampleapp.SqlJobsSecuritySmokeTest.TestConfig;
import java.util.function.Supplier;
import no.bekk.dbscheduler.ui.controller.ConfigController;
import no.bekk.dbscheduler.uistarter.config.DbSchedulerUiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Verifies that the SQL-job endpoints obey the application's own security rules: reads for any
 * authenticated user, mutations for admins only. Mirrors {@link SmokeSpringSecurityTest}.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = ExampleApp.class,
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:sqlsecuritydb;DB_CLOSE_DELAY=-1",
    })
@Import(TestConfig.class)
class SqlJobsSecuritySmokeTest {

  @LocalServerPort private int serverPort;

  @Autowired private TestRestTemplate restTemplate;

  private String api;
  private TestRestTemplate userRestTemplate;
  private TestRestTemplate adminRestTemplate;

  @BeforeEach
  void setUp() {
    api = "http://localhost:" + serverPort + "/db-scheduler-api";
    userRestTemplate = restTemplate.withBasicAuth("user", "user");
    adminRestTemplate = restTemplate.withBasicAuth("admin", "admin");
  }

  @Configuration
  @EnableWebSecurity
  static class TestConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
      return http.csrf(CsrfConfigurer::disable)
          .authorizeHttpRequests(
              authz ->
                  authz
                      .requestMatchers("/db-scheduler/**")
                      .hasAnyRole("ADMIN", "USER")
                      .requestMatchers(HttpMethod.GET, "/db-scheduler-api/**")
                      .hasAnyRole("ADMIN", "USER")
                      .requestMatchers(HttpMethod.POST, "/db-scheduler-api/**")
                      .hasAnyRole("ADMIN")
                      .requestMatchers(HttpMethod.PUT, "/db-scheduler-api/**")
                      .hasAnyRole("ADMIN")
                      .requestMatchers(HttpMethod.DELETE, "/db-scheduler-api/**")
                      .hasAnyRole("ADMIN")
                      .anyRequest()
                      .permitAll())
          .httpBasic(withDefaults())
          .build();
    }

    @Bean
    UserDetailsManager userDetailsService() {
      UserDetails user = User.withUsername("user").password("{noop}user").roles("USER").build();
      UserDetails admin = User.withUsername("admin").password("{noop}admin").roles("ADMIN").build();
      return new InMemoryUserDetailsManager(user, admin);
    }

    @Bean
    ConfigController configController(DbSchedulerUiProperties properties) {
      return new ConfigController(
          properties.history(), properties.overview(), properties::sql, readOnly(properties));
    }

    private Supplier<Boolean> readOnly(DbSchedulerUiProperties properties) {
      return () -> properties.readOnly() || !isAdmin();
    }

    private boolean isAdmin() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      if (auth == null) {
        return false;
      }
      return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
  }

  private static HttpEntity<String> json(String body) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return new HttpEntity<>(body, headers);
  }

  /** Creates a connection and a job as admin; returns [connectionId, jobId]. */
  private String[] createConnectionAndJob() throws Exception {
    ResponseEntity<String> connection =
        adminRestTemplate.postForEntity(
            api + "/sql-connections",
            json(
                "{\"name\":\"sec\",\"dialect\":\"GENERIC\","
                    + "\"jdbcUrl\":\"jdbc:h2:mem:secjobs;DB_CLOSE_DELAY=-1\",\"username\":\"sa\"}"),
            String.class);
    assertThat(connection.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String connectionId =
        com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(connection.getBody())
            .get("id")
            .asText();

    ResponseEntity<String> job =
        adminRestTemplate.postForEntity(
            api + "/sql-jobs",
            json(
                "{\"name\":\"sec-job\",\"connectionId\":\""
                    + connectionId
                    + "\",\"scripts\":[\"SELECT 1;\"],\"cronExpression\":\"0 0 5 * * ?\"}"),
            String.class);
    assertThat(job.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    String jobId =
        com.fasterxml.jackson.databind.json.JsonMapper.builder()
            .build()
            .readTree(job.getBody())
            .get("jobId")
            .asText();
    return new String[] {connectionId, jobId};
  }

  @Test
  void sqlApiWithoutCredentialsIsUnauthorized() {
    ResponseEntity<String> result = restTemplate.getForEntity(api + "/sql-jobs", String.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  void userCanReadSqlJobsAndConnections() {
    assertThat(userRestTemplate.getForEntity(api + "/sql-jobs", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            userRestTemplate.getForEntity(api + "/sql-connections", String.class).getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void userCannotCreateConnection() {
    ResponseEntity<String> result =
        userRestTemplate.postForEntity(
            api + "/sql-connections",
            json("{\"name\":\"nope\",\"dialect\":\"GENERIC\",\"jdbcUrl\":\"jdbc:h2:mem:x\"}"),
            String.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  void userCannotRunOrDeleteJobs() throws Exception {
    String[] ids = createConnectionAndJob();
    ResponseEntity<String> runNow =
        userRestTemplate.exchange(
            api + "/sql-jobs/" + ids[1] + "/run-now",
            HttpMethod.POST,
            HttpEntity.EMPTY,
            String.class);
    assertThat(runNow.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    ResponseEntity<String> delete =
        userRestTemplate.exchange(
            api + "/sql-jobs/" + ids[1], HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
    assertThat(delete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    adminRestTemplate.exchange(
        api + "/sql-jobs/" + ids[1], HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
    adminRestTemplate.exchange(
        api + "/sql-connections/" + ids[0], HttpMethod.DELETE, HttpEntity.EMPTY, String.class);
  }

  @Test
  void adminCanCreateRunAndDelete() throws Exception {
    String[] ids = createConnectionAndJob();

    ResponseEntity<String> runNow =
        adminRestTemplate.exchange(
            api + "/sql-jobs/" + ids[1] + "/run-now",
            HttpMethod.POST,
            HttpEntity.EMPTY,
            String.class);
    assertThat(runNow.getStatusCode()).isEqualTo(HttpStatus.OK);

    ResponseEntity<String> executions =
        adminRestTemplate.getForEntity(
            api + "/sql-jobs/" + ids[1] + "/executions?limit=10", String.class);
    assertThat(executions.getStatusCode()).isEqualTo(HttpStatus.OK);

    assertThat(
            adminRestTemplate
                .exchange(
                    api + "/sql-jobs/" + ids[1], HttpMethod.DELETE, HttpEntity.EMPTY, String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThat(
            adminRestTemplate
                .exchange(
                    api + "/sql-connections/" + ids[0],
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    String.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
  }

  @Test
  void configExposesSqlEnabled() {
    ResponseEntity<String> result = userRestTemplate.getForEntity(api + "/config", String.class);
    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).contains("\"showSql\":true");
    // users feature is off in this profile: the SPA must not show a login screen
    assertThat(result.getBody()).contains("\"usersEnabled\":false");
  }
}
