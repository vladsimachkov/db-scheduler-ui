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

import { getUsersEnabled } from 'src/utils/config';

export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string) ??
  window.location.origin + (window.CONTEXT_PATH || '') + '/db-scheduler-api';

/** Error carrying the HTTP status and the backend's ProblemDetail message, if any. */
export class ApiError extends Error {
  public readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

/**
 * Sends the browser back to the SPA entry so the security layer can re-authenticate.
 * Honours window.CONTEXT_PATH (injected into index.html by the backend) so the redirect
 * works when the UI is deployed under a non-root context path.
 */
export function redirectToUi(): void {
  document.location.href =
    window.location.origin + (window.CONTEXT_PATH || '') + '/db-scheduler';
}

/** Extracts a human message from an RFC 7807 ProblemDetail body, falling back to the status text. */
export async function problemDetailMessage(
  response: Response,
): Promise<string> {
  try {
    const body = (await response.json()) as Record<string, unknown>;
    const detail = typeof body.detail === 'string' ? body.detail : '';
    const title = typeof body.title === 'string' ? body.title : '';
    return detail || title || response.statusText;
  } catch {
    return response.statusText;
  }
}

/**
 * Dispatched on any API 401 while the built-in users feature is on; useAuth listens and drops the
 * UI back to the login screen. A full-page redirect would throw away SPA state for no reason.
 */
export const UNAUTHORIZED_EVENT = 'db-scheduler-ui:unauthorized';

async function request<T>(
  path: string,
  init?: RequestInit,
  opts?: { skipAuthHandling?: boolean },
): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    // merged, not replaced: a caller-provided header must not drop the CSRF guard
    headers: {
      'Content-Type': 'application/json',
      // CSRF guard of the built-in login: cross-site form posts cannot set custom headers
      'X-Requested-With': 'XMLHttpRequest',
      ...(init?.headers as Record<string, string> | undefined),
    },
  });

  if (response.status === 401 && !opts?.skipAuthHandling) {
    if (getUsersEnabled()) {
      window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    } else {
      // host-managed security: the entry page is expected to bounce to the login provider
      redirectToUi();
    }
    throw new ApiError(401, 'Unauthorized');
  }
  if (!response.ok) {
    throw new ApiError(response.status, await problemDetailMessage(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const text = await response.text();
  return (text ? (JSON.parse(text) as T) : (undefined as T));
}

export const apiGet = <T>(path: string): Promise<T> => request<T>(path);

export const apiPost = <T>(path: string, body?: unknown): Promise<T> =>
  request<T>(path, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body),
  });

/**
 * POST without the global 401 handling — for the login endpoint itself, where 401 means
 * "wrong credentials" and must surface as a form error, not a session-expired event.
 */
export const apiPostLogin = <T>(path: string, body?: unknown): Promise<T> =>
  request<T>(
    path,
    { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) },
    { skipAuthHandling: true },
  );

export const apiPut = <T>(path: string, body?: unknown): Promise<T> =>
  request<T>(path, { method: 'PUT', body: JSON.stringify(body) });

export const apiDelete = (path: string): Promise<void> =>
  request<void>(path, { method: 'DELETE' });
