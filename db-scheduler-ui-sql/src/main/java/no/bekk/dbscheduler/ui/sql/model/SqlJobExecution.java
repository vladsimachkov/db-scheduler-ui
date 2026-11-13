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

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

/** One row of {@code sql_job_executions}: the full history record of a single SQL job run. */
public record SqlJobExecution(
    // Snowflake ids exceed Number.MAX_SAFE_INTEGER (2^53) — serialize as string so
    // JavaScript clients keep full precision.
    @JsonFormat(shape = JsonFormat.Shape.STRING) long id,
    String jobId,
    String taskInstance,
    String connectionId,
    SqlDialect dialect,
    String scriptText,
    Instant timeStarted,
    Instant timeFinished,
    boolean succeeded,
    int statementsExecuted,
    long rowsAffected,
    boolean rowsCapped,
    long durationMs,
    String errorClass,
    String errorMessage,
    TriggerType triggeredBy) {}
