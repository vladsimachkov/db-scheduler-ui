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
import {
  ConnectionTestResult,
  SqlConnection,
  SqlConnectionRequest,
} from 'src/models/SqlConnection';
import { apiDelete, apiGet, apiPost, apiPut } from 'src/services/apiClient';

export const CONNECTIONS_QUERY_KEY = 'sql-connections';

export const listConnections = (): Promise<SqlConnection[]> =>
  apiGet<SqlConnection[]>('/sql-connections');

export const createConnection = (
  request: SqlConnectionRequest,
): Promise<SqlConnection> =>
  apiPost<SqlConnection>('/sql-connections', request);

export const updateConnection = (
  id: string,
  request: SqlConnectionRequest,
): Promise<SqlConnection> =>
  apiPut<SqlConnection>(`/sql-connections/${id}`, request);

export const deleteConnection = (id: string): Promise<void> =>
  apiDelete(`/sql-connections/${id}`);

export const testConnection = (id: string): Promise<ConnectionTestResult> =>
  apiPost<ConnectionTestResult>(`/sql-connections/${id}/test`);
