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
import { SqlDialect } from 'src/models/SqlConnection';

export interface SqlJob {
  jobId: string;
  name: string;
  description: string | null;
  connectionId: string;
  connectionName: string | null;
  script: string;
  timeoutSeconds: number;
  cronExpression: string | null;
  zoneId: string | null;
  recurring: boolean;
  nextRunTime: string | null;
  lastSuccess: string | null;
  lastFailure: string | null;
  consecutiveFailures: number;
  running: boolean;
}

export interface SqlJobRequest {
  name: string;
  description?: string;
  connectionId: string;
  script: string;
  timeoutSeconds?: number;
  cronExpression?: string;
  zoneId?: string;
  runAt?: string;
}

export interface SqlJobExecution {
  /** Snowflake id — the backend serializes it as a string (exceeds Number.MAX_SAFE_INTEGER). */
  id: string;
  jobId: string;
  taskInstance: string;
  connectionId: string;
  dialect: SqlDialect | null;
  scriptText: string | null;
  timeStarted: string;
  timeFinished: string;
  succeeded: boolean;
  statementsExecuted: number;
  rowsAffected: number;
  rowsCapped: boolean;
  durationMs: number;
  errorClass: string | null;
  errorMessage: string | null;
  triggeredBy: 'SCHEDULE' | 'MANUAL';
}
