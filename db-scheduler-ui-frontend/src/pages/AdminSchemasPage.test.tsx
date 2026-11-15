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
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminSchemasPage from 'src/pages/AdminSchemasPage';
import i18n from 'src/i18n';
import { Schema } from 'src/models/Schema';
import { User } from 'src/models/User';
import {
  createSchema,
  grantAccess,
  listGrants,
  listSchemas,
  revokeAccess,
  updateSchema,
} from 'src/services/schemas';
import { listUsers } from 'src/services/users';

// config.ts fails safe to readOnly when /config is unreachable — in tests that hides every button
vi.mock('src/utils/config', () => ({
  getReadonly: () => false,
  getUsersEnabled: () => true,
}));
vi.mock('src/services/schemas', () => ({
  SCHEMAS_QUERY_KEY: 'sql-schemas',
  GRANTS_QUERY_KEY: 'schema-grants',
  listSchemas: vi.fn(),
  createSchema: vi.fn(),
  updateSchema: vi.fn(),
  deleteSchema: vi.fn(),
  listGrants: vi.fn(),
  grantAccess: vi.fn(),
  revokeAccess: vi.fn(),
}));
vi.mock('src/services/users', () => ({
  USERS_QUERY_KEY: 'users',
  listUsers: vi.fn(),
}));

beforeAll(async () => {
  await i18n.changeLanguage('en');
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
});

const main: Schema = {
  id: 's1',
  name: 'Main',
  description: 'primary',
  createdTime: '2026-01-01T00:00:00Z',
  updatedTime: '2026-01-01T00:00:00Z',
};

const bob: User = {
  id: 'u2',
  username: 'bob',
  admin: false,
  enabled: true,
  createdTime: '2026-01-01T00:00:00Z',
  updatedTime: '2026-01-01T00:00:00Z',
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <ChakraProvider>
      <QueryClientProvider client={queryClient}>
        <AdminSchemasPage />
      </QueryClientProvider>
    </ChakraProvider>,
  );
}

describe('AdminSchemasPage', () => {
  it('lists schemas', async () => {
    vi.mocked(listSchemas).mockResolvedValue([main]);
    renderPage();
    expect(await screen.findByText('Main')).toBeInTheDocument();
    expect(screen.getByText('primary')).toBeInTheDocument();
  });

  it('creates a schema from the modal', async () => {
    vi.mocked(listSchemas).mockResolvedValue([]);
    vi.mocked(createSchema).mockResolvedValue(main);
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: 'Add schema' }));
    fireEvent.change(await screen.findByLabelText(/^Name/), {
      target: { value: 'Main' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(createSchema).toHaveBeenCalledWith({
        name: 'Main',
        description: undefined,
      }),
    );
  });

  it('grants access and refetches the list', async () => {
    vi.mocked(listSchemas).mockResolvedValue([main]);
    vi.mocked(listUsers).mockResolvedValue([bob]);
    // first fetch: nobody; after the grant invalidates: bob
    vi.mocked(listGrants).mockResolvedValueOnce([]).mockResolvedValue(['bob']);
    vi.mocked(grantAccess).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('Main');

    fireEvent.click(screen.getByRole('button', { name: 'Access' }));
    expect(await screen.findByText('No one has access yet')).toBeInTheDocument();

    // the candidate picker is the only combobox in the panel
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'bob' } });
    fireEvent.click(screen.getByRole('button', { name: 'Grant' }));
    await waitFor(() => expect(grantAccess).toHaveBeenCalledWith('s1', 'bob'));
    // the panel shows the new grant without a reopen
    expect(await screen.findByText('bob')).toBeInTheDocument();
  });

  it('revokes access from the panel', async () => {
    vi.mocked(listSchemas).mockResolvedValue([main]);
    vi.mocked(listUsers).mockResolvedValue([bob]);
    vi.mocked(listGrants).mockResolvedValue(['bob']);
    vi.mocked(revokeAccess).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('Main');

    fireEvent.click(screen.getByRole('button', { name: 'Access' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Revoke' }));

    await waitFor(() => expect(revokeAccess).toHaveBeenCalledWith('s1', 'bob'));
  });

  it('edits an existing schema', async () => {
    vi.mocked(listSchemas).mockResolvedValue([main]);
    vi.mocked(updateSchema).mockResolvedValue(main);
    renderPage();
    await screen.findByText('Main');

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    const name = await screen.findByLabelText(/^Name/);
    expect(name).toHaveValue('Main');
    fireEvent.change(name, { target: { value: 'Renamed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(updateSchema).toHaveBeenCalledWith('s1', {
        name: 'Renamed',
        description: 'primary',
      }),
    );
  });
});
