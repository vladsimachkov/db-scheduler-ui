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
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiGet, apiPost } from 'src/services/apiClient';

function mockResponse(
  status: number,
  options: { body?: unknown; statusText?: string; text?: string } = {},
): Response {
  const text =
    options.text ?? (options.body === undefined ? '' : JSON.stringify(options.body));
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: options.statusText ?? '',
    json: async () => {
      if (options.body === undefined) {
        throw new Error('no json body');
      }
      return options.body;
    },
    text: async () => text,
  } as unknown as Response;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('apiClient', () => {
  it('sends the CSRF header on every request', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(mockResponse(200, { body: { a: 1 } }));
    vi.stubGlobal('fetch', fetchMock);

    await apiGet<{ a: number }>('/thing');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Requested-With']).toBe('XMLHttpRequest');
  });

  it('parses a JSON success body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse(200, { body: { a: 1 } })));
    await expect(apiGet<{ a: number }>('/thing')).resolves.toEqual({ a: 1 });
  });

  it('returns undefined for 204 no-content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse(204)));
    await expect(apiGet<void>('/thing')).resolves.toBeUndefined();
  });

  it('returns undefined for a 200 with an empty body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse(200)));
    await expect(apiGet<void>('/thing')).resolves.toBeUndefined();
  });

  it('prefers ProblemDetail detail over title', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          mockResponse(400, { body: { title: 'Bad', detail: 'cron is invalid' } }),
        ),
    );
    await expect(apiGet('/thing')).rejects.toThrow('cron is invalid');
  });

  it('falls back to ProblemDetail title, then statusText', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(mockResponse(404, { body: { title: 'Not Found' } })),
    );
    await expect(apiGet('/thing')).rejects.toThrow('Not Found');

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(mockResponse(500, { text: 'oops', statusText: 'Server Error' })),
    );
    await expect(apiGet('/thing')).rejects.toThrow('Server Error');
  });

  it('carries the HTTP status on ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(mockResponse(409, { body: { detail: 'job is running' } })),
    );
    const error = await apiGet('/thing').catch((e) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
  });

  it('sends a JSON body on POST', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(mockResponse(201, { body: { ok: true } }));
    vi.stubGlobal('fetch', fetchMock);
    await apiPost('/thing', { x: 1 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/thing');
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"x":1}');
  });
});
