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
import { apiGet, apiPost, apiPostLogin } from 'src/services/apiClient';

/** Mirrors the backend AuthUserDto; anonymous callers get authenticated=false, not a 401. */
export interface AuthUser {
  authenticated: boolean;
  username: string | null;
  admin: boolean;
}

export const AUTH_ME_QUERY_KEY = 'auth-me';

export const ANONYMOUS: AuthUser = {
  authenticated: false,
  username: null,
  admin: false,
};

export const getMe = (): Promise<AuthUser> => apiGet<AuthUser>('/auth/me');

export const login = (
  username: string,
  password: string,
): Promise<AuthUser> =>
  apiPostLogin<AuthUser>('/auth/login', { username, password });

export const logout = (): Promise<void> => apiPost<void>('/auth/logout');
