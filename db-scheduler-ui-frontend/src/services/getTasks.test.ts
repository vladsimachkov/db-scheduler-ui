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
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getTasks } from 'src/services/getTasks';
import { apiGet } from 'src/services/apiClient';
import { FilterBy } from 'src/models/QueryParams';

vi.mock('src/services/apiClient', () => ({ apiGet: vi.fn() }));

const base = { filter: FilterBy.All };

beforeEach(() => {
  vi.resetAllMocks();
  vi.mocked(apiGet).mockResolvedValue({
    numberOfItems: 0,
    numberOfPages: 0,
    items: [],
  });
});

describe('getTasks schemaId param', () => {
  it('appends schemaId when a schema filter is active', async () => {
    await getTasks({ ...base, schemaId: 'schema-x' });

    const url = vi.mocked(apiGet).mock.calls[0][0];
    expect(url).toContain('schemaId=schema-x');
  });

  it('omits schemaId when no schema filter is active', async () => {
    await getTasks(base);

    const url = vi.mocked(apiGet).mock.calls[0][0];
    expect(url).not.toContain('schemaId');
  });

  it('omits an empty schemaId', async () => {
    await getTasks({ ...base, schemaId: '' });

    const url = vi.mocked(apiGet).mock.calls[0][0];
    expect(url).not.toContain('schemaId');
  });
});
