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
import { SqlJob } from 'src/models/SqlJob';
import { createJob, updateJob } from 'src/services/sqlJobs';

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

function renderForm(connections: SqlConnection[], initial?: SqlJob) {
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
          initial={initial}
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

  it('drops the job\'s own connection when the schema moves off it', () => {
    // a job homed to s1/c1: keeping the job's own connection listed is an
    // exception for schema-less legacy connections only, not a blanket one
    renderForm([inMain, inOther], { ...legacyJob, schemaId: 's1', connectionId: 'c1' });
    expect(connectionOptionNames()).toEqual(['In Main']);

    fireEvent.change(schemaSelect(), { target: { value: 's2' } });
    expect(connectionOptionNames()).toEqual(['In Other']);
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

const legacyJob: SqlJob = {
  jobId: 'j1',
  name: 'legacy',
  description: null,
  schemaId: null,
  schemaName: null,
  connectionId: 'c1',
  connectionName: 'In Main',
  scripts: ['SELECT 1;'],
  timeoutSeconds: 300,
  cronExpression: '0 0 * * *',
  recurring: true,
  nextRunTime: null,
  lastSuccess: null,
  lastFailure: null,
  consecutiveFailures: 0,
  running: false,
};

describe('SqlJobForm with a single accessible schema', () => {
  function renderSingle(
    initial?: SqlJob,
    connections: SqlConnection[] = [inMain],
    initialSchemas: Schema[] = [schemas[0]],
  ) {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
    });
    const tree = (list: Schema[]) => (
      <ChakraProvider>
        <QueryClientProvider client={queryClient}>
          <SqlJobForm
            isOpen
            onClose={vi.fn()}
            connections={connections}
            schemas={list}
            initial={initial}
          />
        </QueryClientProvider>
      </ChakraProvider>
    );
    const view = render(tree(initialSchemas));
    return { rerender: (list: Schema[]) => view.rerender(tree(list)) };
  }

  it('hides the picker and preselects the only schema for the connection filter', () => {
    renderSingle();
    expect(screen.queryByLabelText('Schema')).not.toBeInTheDocument();
    // the auto-filled schema drives the connection filter — c1 belongs to s1
    expect(connectionOptionNames()).toContain('In Main');
  });

  it('assigns the only schema when editing a legacy schema-less job', async () => {
    renderSingle(legacyJob);
    expect(screen.queryByLabelText('Schema')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(updateJob).toHaveBeenCalled());
    expect(vi.mocked(updateJob).mock.calls[0][1]).toMatchObject({
      schemaId: 's1',
    });
  });

  it('keeps a legacy schema-less connection visible when the schema auto-fills', () => {
    // a pre-users job whose connection never got a schema: the auto-filled
    // schema must not blank out the job's own connection in the dropdown
    const legacyConn: SqlConnection = { ...inMain, id: 'c0', name: 'Legacy', schemaId: null };
    renderSingle(
      { ...legacyJob, connectionId: 'c0', connectionName: 'Legacy' },
      [legacyConn],
    );
    expect(screen.queryByLabelText('Schema')).not.toBeInTheDocument();
    expect(connectionSelect()).toHaveValue('c0');
    expect(within(connectionSelect()).getByRole('option', { name: 'Legacy' })).toBeInTheDocument();
  });

  it('blocks saving a legacy job whose connection has no schema', async () => {
    // the backend would answer 400 (same-schema rule) — the form says it first
    const legacyConn: SqlConnection = { ...inMain, id: 'c0', name: 'Legacy', schemaId: null };
    renderSingle({ ...legacyJob, connectionId: 'c0', connectionName: 'Legacy' }, [legacyConn]);

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText(/must belong to the job's schema/)).toBeInTheDocument();
    await waitFor(() => expect(updateJob).not.toHaveBeenCalled());
  });

  it('applies the auto-fill when the schema list arrives after the modal opened', async () => {
    const legacyConn: SqlConnection = { ...inMain, id: 'c0', name: 'Legacy', schemaId: null };
    const job = { ...legacyJob, connectionId: 'c0', connectionName: 'Legacy' };
    // the /sql-schemas query is still pending at mount: the picker is visible
    const { rerender: resolveSchemas } = renderSingle(job, [legacyConn], []);
    expect(screen.getByLabelText('Schema')).toBeInTheDocument();

    resolveSchemas([schemas[0]]);
    await waitFor(() =>
      expect(screen.queryByLabelText('Schema')).not.toBeInTheDocument(),
    );
    // the late auto-fill must not wipe the job's own connection (onSchemaChange
    // would have cleared it as schema-mismatched)
    expect(connectionSelect()).toHaveValue('c0');

    // the hint on save proves both halves landed: schemaId auto-filled to s1
    // and connectionId kept as the schema-less c0 (a doomed pair, blocked locally)
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText(/must belong to the job's schema/)).toBeInTheDocument();
    expect(updateJob).not.toHaveBeenCalled();
  });
});
