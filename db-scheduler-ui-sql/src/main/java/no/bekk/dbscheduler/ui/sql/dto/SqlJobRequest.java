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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Create/update payload for a SQL job. Recurring when {@code cronExpression} is set, one-time
 * otherwise (then {@code runAt} optionally picks the execution time, defaulting to now).
 */
public record SqlJobRequest(
    @NotBlank @Size(max = 100) String name,
    @Size(max = 1000) String description,
    @NotBlank String connectionId,
    @NotBlank @Size(max = 65536, message = "script must not exceed 64 KiB") String script,
    @Min(1) @Max(86400) Integer timeoutSeconds,
    @Size(max = 100) String cronExpression,
    Instant runAt) {

  public boolean isRecurring() {
    return cronExpression != null && !cronExpression.isBlank();
  }
}
