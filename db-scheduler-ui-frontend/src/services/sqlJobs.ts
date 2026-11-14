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
import { SqlJob, SqlJobExecution, SqlJobRequest } from 'src/models/SqlJob';
import { apiDelete, apiGet, apiPost, apiPut } from 'src/services/apiClient';

export const JOBS_QUERY_KEY = 'sql-jobs';

export const listJobs = (): Promise<SqlJob[]> => apiGet<SqlJob[]>('/sql-jobs');

export const getJob = (jobId: string): Promise<SqlJob> =>
  apiGet<SqlJob>(`/sql-jobs/${jobId}`);

export const createJob = (request: SqlJobRequest): Promise<SqlJob> =>
  apiPost<SqlJob>('/sql-jobs', request);

export const updateJob = (
  jobId: string,
  request: SqlJobRequest,
): Promise<SqlJob> => apiPut<SqlJob>(`/sql-jobs/${jobId}`, request);

export const deleteJob = (jobId: string): Promise<void> =>
  apiDelete(`/sql-jobs/${jobId}`);

export const runJobNow = (jobId: string): Promise<void> =>
  apiPost<void>(`/sql-jobs/${jobId}/run-now`);

export const listExecutions = (
  jobId: string,
  limit = 50,
): Promise<SqlJobExecution[]> =>
  apiGet<SqlJobExecution[]>(`/sql-jobs/${jobId}/executions?limit=${limit}`);

export const previewSchedule = (
  cron: string,
  count = 3,
): Promise<string[]> => {
  const params = new URLSearchParams({ cron, count: String(count) });
  return apiGet<string[]>(`/sql-jobs/preview-schedule?${params}`);
};
