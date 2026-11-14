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
package no.bekk.dbscheduler.ui.sql.autoconfigure;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTaskWithPersistentSchedule;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import javax.sql.DataSource;
import no.bekk.dbscheduler.ui.controller.SqlConnectionAdminController;
import no.bekk.dbscheduler.ui.controller.SqlConnectionController;
import no.bekk.dbscheduler.ui.controller.SqlJobAdminController;
import no.bekk.dbscheduler.ui.controller.SqlJobController;
import no.bekk.dbscheduler.ui.sql.config.DbSchedulerUiSqlProperties;
import no.bekk.dbscheduler.ui.sql.crypto.CredentialCipher;
import no.bekk.dbscheduler.ui.sql.exec.SqlJobExecutor;
import no.bekk.dbscheduler.ui.sql.model.SqlJobData;
import no.bekk.dbscheduler.ui.sql.model.TriggerType;
import no.bekk.dbscheduler.ui.sql.pool.ConnectionPoolManager;
import no.bekk.dbscheduler.ui.sql.repo.SqlConnectionRepository;
import no.bekk.dbscheduler.ui.sql.repo.SqlJobExecutionRepository;
import no.bekk.dbscheduler.ui.sql.service.SqlAccessGuard;
import no.bekk.dbscheduler.ui.sql.service.SqlConnectionService;
import no.bekk.dbscheduler.ui.sql.service.SqlJobService;
import no.bekk.dbscheduler.ui.users.repo.SchemaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Wires the SQL job engine, gated on {@code db-scheduler-ui.sql=true}.
 *
 * <p>Registers the two generic task definitions ({@code sql-script-task} and {@code
 * sql-script-once-task}) plus the REST controllers. The scheduler's own {@code DataSource} doubles
 * as the store for the connection registry and execution history.
 */
@AutoConfiguration(
    afterName = "com.github.kagkarlsson.scheduler.boot.autoconfigure.DbSchedulerAutoConfiguration")
@ConditionalOnProperty(prefix = "db-scheduler-ui", name = "sql", havingValue = "true")
@ConditionalOnBean(SchedulerClient.class)
@EnableConfigurationProperties(DbSchedulerUiSqlProperties.class)
public class DbSchedulerUiSqlAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(DbSchedulerUiSqlAutoConfiguration.class);

  public static final TaskDescriptor<SqlJobData> SQL_SCRIPT_TASK =
      TaskDescriptor.of("sql-script-task", SqlJobData.class);
  public static final TaskDescriptor<SqlJobData> SQL_SCRIPT_ONCE_TASK =
      TaskDescriptor.of("sql-script-once-task", SqlJobData.class);

  @Bean
  CredentialCipher credentialCipher(DbSchedulerUiSqlProperties properties) {
    CredentialCipher cipher = new CredentialCipher(properties.encryptionKey());
    if (!cipher.isEnabled()) {
      log.warn(
          "db-scheduler-ui.sql.encryption-key is not set — connection passwords will be stored"
              + " in PLAINTEXT. Configure a base64-encoded 256-bit key in production.");
    }
    return cipher;
  }

  @Bean
  ConnectionPoolManager connectionPoolManager(CredentialCipher cipher) {
    return new ConnectionPoolManager(cipher);
  }

  @Bean
  @ConditionalOnMissingBean(JdbcOperations.class)
  JdbcTemplate sqlJobJdbcTemplate(DataSource dataSource) {
    return new JdbcTemplate(dataSource);
  }

  @Bean
  SqlConnectionRepository sqlConnectionRepository(JdbcTemplate jdbcTemplate) {
    return new SqlConnectionRepository(jdbcTemplate);
  }

  @Bean
  SqlJobExecutionRepository sqlJobExecutionRepository(JdbcTemplate jdbcTemplate) {
    return new SqlJobExecutionRepository(jdbcTemplate);
  }

  @Bean
  SqlJobExecutor sqlJobExecutor(
      SqlConnectionRepository connections,
      SqlJobExecutionRepository executions,
      ConnectionPoolManager pools,
      DbSchedulerUiSqlProperties properties) {
    return new SqlJobExecutor(connections, executions, pools, properties);
  }

  @Bean
  RecurringTaskWithPersistentSchedule<SqlJobData> sqlScriptTask(SqlJobExecutor executor) {
    return Tasks.recurringWithPersistentSchedule(SQL_SCRIPT_TASK)
        .execute(
            (inst, ctx) -> executor.execute(inst.getData(), inst.getId(), TriggerType.SCHEDULE));
  }

  @Bean
  OneTimeTask<SqlJobData> sqlScriptOnceTask(SqlJobExecutor executor) {
    return Tasks.oneTime(SQL_SCRIPT_ONCE_TASK)
        .execute((inst, ctx) -> executor.execute(inst.getData(), inst.getId(), TriggerType.MANUAL));
  }

  @Bean
  SqlJobService sqlJobService(
      SchedulerClient client,
      RecurringTaskWithPersistentSchedule<SqlJobData> sqlScriptTask,
      OneTimeTask<SqlJobData> sqlScriptOnceTask,
      SqlConnectionRepository connections,
      SqlJobExecutionRepository executions,
      ObjectProvider<SqlAccessGuard> guard,
      ObjectProvider<SchemaRepository> schemas) {
    // The guard and schema-repository beans only exist when the users feature is on; without them
    // every job is visible and carries no schema name. getIfAvailable (not getIfUnique) is
    // deliberate: a second guard bean would fail startup loudly rather than silently disabling
    // enforcement.
    return new SqlJobService(
        client,
        sqlScriptTask,
        sqlScriptOnceTask,
        connections,
        executions,
        guard.getIfAvailable(),
        schemas.getIfAvailable());
  }

  @Bean
  SqlConnectionService sqlConnectionService(
      SqlConnectionRepository repository,
      ConnectionPoolManager pools,
      CredentialCipher cipher,
      SchedulerClient client,
      ObjectProvider<SqlAccessGuard> guard) {
    return new SqlConnectionService(repository, pools, cipher, client, guard.getIfAvailable());
  }

  @Bean
  SqlJobController sqlJobController(SqlJobService jobs) {
    return new SqlJobController(jobs);
  }

  @Bean
  SqlConnectionController sqlConnectionController(SqlConnectionService connections) {
    return new SqlConnectionController(connections);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "db-scheduler-ui",
      name = "read-only",
      havingValue = "false",
      matchIfMissing = true)
  SqlJobAdminController sqlJobAdminController(SqlJobService jobs) {
    return new SqlJobAdminController(jobs);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "db-scheduler-ui",
      name = "read-only",
      havingValue = "false",
      matchIfMissing = true)
  SqlConnectionAdminController sqlConnectionAdminController(SqlConnectionService connections) {
    return new SqlConnectionAdminController(connections);
  }
}
