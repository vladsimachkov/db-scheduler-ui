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
import { User } from 'src/models/User';

export const USERS_QUERY_KEY = 'users';

export interface UserCreateRequest {
  username: string;
  password: string;
}

/** Both flags optional: the backend leaves untouched whatever is omitted. */
export interface UserUpdateRequest {
  admin?: boolean;
  enabled?: boolean;
}

export const listUsers = (): Promise<User[]> => apiGet<User[]>('/users');

export const createUser = (request: UserCreateRequest): Promise<User> =>
  apiPost<User>('/users', request);

export const updateUser = (id: string, request: UserUpdateRequest): Promise<User> =>
  apiPut<User>(`/users/${id}`, request);

export const resetPassword = (id: string, password: string): Promise<void> =>
  apiPost<void>(`/users/${id}/password`, { password });

export const deleteUser = (id: string): Promise<void> =>
  apiDelete(`/users/${id}`);
