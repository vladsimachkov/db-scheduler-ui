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
package no.bekk.dbscheduler.ui.users.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Users-feature settings, active only when {@code db-scheduler-ui.sql=true} as well.
 *
 * @param enabled master gate for the built-in login, users and schemas
 * @param bootstrapUsername username of the admin created at startup when {@code sql_users} is empty
 * @param bootstrapPassword that admin's password; change it after first login
 */
@ConfigurationProperties(prefix = "db-scheduler-ui.users")
public record DbSchedulerUiUsersProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String bootstrapUsername,
    @DefaultValue("") String bootstrapPassword) {}
