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
import { apiDelete, apiGet, apiPost, apiPut } from 'src/services/apiClient';
import { Schema } from 'src/models/Schema';

export const SCHEMAS_QUERY_KEY = 'sql-schemas';

/** The schemas the signed-in user may see (all of them for an admin). */
export const listSchemas = (): Promise<Schema[]> =>
  apiGet<Schema[]>('/sql-schemas');

export interface SchemaRequest {
  name: string;
  description?: string;
}

export const createSchema = (request: SchemaRequest): Promise<Schema> =>
  apiPost<Schema>('/sql-schemas', request);

export const updateSchema = (id: string, request: SchemaRequest): Promise<Schema> =>
  apiPut<Schema>(`/sql-schemas/${id}`, request);

export const deleteSchema = (id: string): Promise<void> =>
  apiDelete(`/sql-schemas/${id}`);

export const GRANTS_QUERY_KEY = 'schema-grants';

/** Usernames holding access to the schema. */
export const listGrants = (id: string): Promise<string[]> =>
  apiGet<string[]>(`/sql-schemas/${id}/grants`);

/** Usernames travel as path variables — encode them like the backend's charset demands. */
export const grantAccess = (id: string, username: string): Promise<void> =>
  apiPut<void>(`/sql-schemas/${id}/grants/${encodeURIComponent(username)}`);

export const revokeAccess = (id: string, username: string): Promise<void> =>
  apiDelete(`/sql-schemas/${id}/grants/${encodeURIComponent(username)}`);
