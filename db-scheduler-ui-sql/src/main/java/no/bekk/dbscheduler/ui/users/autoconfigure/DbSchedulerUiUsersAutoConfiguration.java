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
package no.bekk.dbscheduler.ui.users.autoconfigure;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import no.bekk.dbscheduler.ui.sql.autoconfigure.DbSchedulerUiSqlAutoConfiguration;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.users.auth.AuthController;
import no.bekk.dbscheduler.ui.users.auth.AuthSessionFilter;
import no.bekk.dbscheduler.ui.users.auth.BootstrapAdminInitializer;
import no.bekk.dbscheduler.ui.users.config.DbSchedulerUiUsersProperties;
import no.bekk.dbscheduler.ui.users.crypto.PasswordHasher;
import no.bekk.dbscheduler.ui.users.repo.GrantRepository;
import no.bekk.dbscheduler.ui.users.repo.SchemaRepository;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import no.bekk.dbscheduler.ui.users.service.AuthService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Built-in users, schemas and session login, gated on {@code db-scheduler-ui.users=true}. Requires
 * the SQL feature (schemas group jobs and connections) and a servlet web app — the filter model is
 * servlet-based; WebFlux hosts keep securing the API themselves.
 */
@AutoConfiguration(after = DbSchedulerUiSqlAutoConfiguration.class)
@ConditionalOnProperty(prefix = "db-scheduler-ui.users", name = "enabled", havingValue = "true")
@ConditionalOnBean({SchedulerClient.class, SqlConnectionRepository.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(DbSchedulerUiUsersProperties.class)
public class DbSchedulerUiUsersAutoConfiguration {

  @Bean
  PasswordHasher passwordHasher() {
    return new PasswordHasher();
  }

  @Bean
  UserRepository userRepository(JdbcTemplate jdbcTemplate) {
    return new UserRepository(jdbcTemplate);
  }

  @Bean
  SchemaRepository schemaRepository(JdbcTemplate jdbcTemplate) {
    return new SchemaRepository(jdbcTemplate);
  }

  @Bean
  GrantRepository grantRepository(JdbcTemplate jdbcTemplate) {
    return new GrantRepository(jdbcTemplate);
  }

  @Bean
  AuthService authService(UserRepository users, PasswordHasher hasher) {
    return new AuthService(users, hasher);
  }

  @Bean
  AuthController authController(AuthService auth) {
    return new AuthController(auth);
  }

  @Bean
  BootstrapAdminInitializer bootstrapAdminInitializer(
      UserRepository users, PasswordHasher hasher, DbSchedulerUiUsersProperties properties) {
    return new BootstrapAdminInitializer(users, hasher, properties);
  }

  @Bean
  FilterRegistrationBean<AuthSessionFilter> dbSchedulerUiAuthFilter() {
    FilterRegistrationBean<AuthSessionFilter> registration =
        new FilterRegistrationBean<>(new AuthSessionFilter());
    registration.addUrlPatterns("/*");
    registration.setName("dbSchedulerUiAuthFilter");
    return registration;
  }
}
