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
import '@testing-library/jest-dom/vitest';
import { ChakraProvider } from '@chakra-ui/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import SqlJobForm from 'src/components/sql/SqlJobForm';
import i18n from 'src/i18n';
import { Schema } from 'src/models/Schema';
import { SqlConnection } from 'src/models/SqlConnection';
import { createJob } from 'src/services/sqlJobs';

// mutable so the whole file can run with the users feature on
let usersEnabledFlag = false;
vi.mock('src/utils/config', () => ({ getUsersEnabled: () => usersEnabledFlag }));
vi.mock('src/services/sqlJobs', () => ({
  JOBS_QUERY_KEY: 'sql-jobs',
  listJobs: vi.fn(),
  getJob: vi.fn(),
  createJob: vi.fn(),
  updateJob: vi.fn(),
  deleteJob: vi.fn(),
  runJobNow: vi.fn(),
  listExecutions: vi.fn(),
  previewSchedule: vi.fn(),
}));

beforeAll(async () => {
  // Pin the language: the detector would otherwise follow navigator.language.
  await i18n.changeLanguage('en');

  // Chakra's color-mode hook needs matchMedia, which jsdom does not implement.
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: (query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }),
  });
});

beforeEach(() => {
  vi.resetAllMocks();
  usersEnabledFlag = true;
});

afterEach(() => {
  usersEnabledFlag = false;
});

const schemas: Schema[] = [
  {
    id: 's1',
    name: 'Main',
    description: null,
    createdTime: '2026-01-01T00:00:00Z',
    updatedTime: '2026-01-01T00:00:00Z',
  },
  {
    id: 's2',
    name: 'Other',
    description: null,
    createdTime: '2026-01-01T00:00:00Z',
    updatedTime: '2026-01-01T00:00:00Z',
  },
];

function connection(id: string, name: string, schemaId: string): SqlConnection {
  return {
    id,
    name,
    schemaId,
    dialect: 'POSTGRESQL',
    jdbcUrl: 'jdbc:postgresql://db.example.com:5432/app',
    driverClass: null,
    username: null,
    passwordSet: false,
    propertiesJson: null,
    createdTime: '2026-01-01T00:00:00Z',
    updatedTime: '2026-01-01T00:00:00Z',
  };
}

const inMain = connection('c1', 'In Main', 's1');
const inOther = connection('c2', 'In Other', 's2');

function renderForm(connections: SqlConnection[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <ChakraProvider>
      <QueryClientProvider client={queryClient}>
        <SqlJobForm
          isOpen
          onClose={vi.fn()}
          connections={connections}
          schemas={schemas}
        />
      </QueryClientProvider>
    </ChakraProvider>,
  );
}

function schemaSelect(): HTMLSelectElement {
  return screen.getByLabelText('Schema') as HTMLSelectElement;
}

function connectionSelect(): HTMLSelectElement {
  return screen.getByLabelText('Connection') as HTMLSelectElement;
}

function connectionOptionNames(): string[] {
  return within(connectionSelect())
    .getAllByRole('option')
    // Chakra renders the placeholder as a disabled empty-valued option
    .filter((option) => (option as HTMLOptionElement).value !== '')
    .map((option) => option.textContent ?? '');
}

describe('SqlJobForm with the users feature on', () => {
  it('offers only the connections of the selected schema', () => {
    renderForm([inMain, inOther]);
    // no schema picked yet: every connection is on offer (legacy and admins)
    expect(connectionOptionNames()).toEqual(['In Main', 'In Other']);

    fireEvent.change(schemaSelect(), { target: { value: 's1' } });
    expect(connectionOptionNames()).toEqual(['In Main']);
  });

  it('clears a connection that no longer matches the schema', () => {
    renderForm([inMain, inOther]);
    fireEvent.change(schemaSelect(), { target: { value: 's1' } });
    fireEvent.change(connectionSelect(), { target: { value: 'c1' } });
    expect(connectionSelect()).toHaveValue('c1');

    fireEvent.change(schemaSelect(), { target: { value: 's2' } });
    expect(connectionSelect()).toHaveValue('');
  });

  it('blocks saving until a schema is picked', async () => {
    renderForm([inMain]);
    fireEvent.change(screen.getByLabelText('Name'), {
      target: { value: 'nightly' },
    });
    fireEvent.change(connectionSelect(), { target: { value: 'c1' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('A schema is required')).toBeInTheDocument();
    await waitFor(() => expect(createJob).not.toHaveBeenCalled());
  });
});
