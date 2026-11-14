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
package no.bekk.dbscheduler.ui.sql.dto;

import java.time.Instant;

/** A SQL job as seen by the UI: definition plus live scheduler state. */
public record SqlJobDto(
    String jobId,
    String name,
    String description,
    String connectionId,
    String connectionName,
    String script,
    int timeoutSeconds,
    String cronExpression,
    boolean recurring,
    Instant nextRunTime,
    Instant lastSuccess,
    Instant lastFailure,
    int consecutiveFailures,
    boolean running) {}
