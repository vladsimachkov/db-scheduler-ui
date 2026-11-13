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
package no.bekk.dbscheduler.ui.sql.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for the SQL job engine.
 *
 * @param encryptionKey base64-encoded 256-bit AES key used to encrypt connection passwords; when
 *     blank, passwords are stored in plaintext and a warning is logged at startup
 * @param defaultTimeoutSeconds per-statement query timeout applied when a job does not set its own
 * @param maxRowsPerStatement cap on rows counted for a single statement's result set; larger
 *     results are truncated and flagged {@code rowsCapped}
 */
@Validated
@ConfigurationProperties("db-scheduler-ui.sql")
public record DbSchedulerUiSqlProperties(
    @DefaultValue("") String encryptionKey,
    @Min(1) @DefaultValue("300") int defaultTimeoutSeconds,
    @Min(1) @DefaultValue("10000000") long maxRowsPerStatement) {}
