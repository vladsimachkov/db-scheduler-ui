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

const API_BASE_URL: string =
    (import.meta.env.VITE_API_BASE_URL as string) ??
    window.location.origin + (window.CONTEXT_PATH || '') + '/db-scheduler-api';

// If /config cannot be fetched, fail safe: no optional tabs, and treat the UI as
// read-only so admin actions are not offered against an unreachable backend.
let config: Record<string, unknown> = { readOnly: true };
try {
  const response = await fetch(`${API_BASE_URL}/config`);
  if (response.ok) {
    config = (await response.json()) as Record<string, unknown>;
  }
} catch {
  // Keep the fail-safe defaults above
}

const showHistory =
  'showHistory' in config ? Boolean(config.showHistory) : false;

const showOverview =
  'showOverview' in config ? Boolean(config.showOverview) : false;

const showSql = 'showSql' in config ? Boolean(config.showSql) : false;

const readOnly = 'readOnly' in config ? Boolean(config.readOnly) : false;

const usersEnabled =
  'usersEnabled' in config ? Boolean(config.usersEnabled) : false;

export const getShowHistory = (): boolean => showHistory;
export const getShowOverview = (): boolean => showOverview;
export const getShowSql = (): boolean => showSql;
export const getReadonly = (): boolean => readOnly;
export const getUsersEnabled = (): boolean => usersEnabled;
