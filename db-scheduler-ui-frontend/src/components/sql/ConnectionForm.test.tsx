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
import ConnectionForm from 'src/components/sql/ConnectionForm';
import i18n from 'src/i18n';
import { SqlConnection } from 'src/models/SqlConnection';
import {
  createConnection,
  testConnection,
  updateConnection,
} from 'src/services/sqlConnections';
import { listSchemas } from 'src/services/schemas';

// mutable so one describe can flip the users feature on
let usersEnabledFlag = false;
vi.mock('src/utils/config', () => ({ getUsersEnabled: () => usersEnabledFlag }));
vi.mock('src/services/schemas', () => ({
  SCHEMAS_QUERY_KEY: 'sql-schemas',
  listSchemas: vi.fn(),
}));

vi.mock('src/services/sqlConnections', () => ({
  CONNECTIONS_QUERY_KEY: 'sql-connections',
  listConnections: vi.fn(),
  createConnection: vi.fn(),
  updateConnection: vi.fn(),
  deleteConnection: vi.fn(),
  testConnection: vi.fn(),
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
  // reset, not clear: mockResolvedValue from a previous test must not leak
  vi.resetAllMocks();
});

const editedConnection: SqlConnection = {
  id: 'c1',
  name: 'demo',
  schemaId: null,
  dialect: 'POSTGRESQL',
  jdbcUrl: 'jdbc:postgresql://db.example.com:5432/app',
  driverClass: null,
  username: 'app',
  passwordSet: true,
  propertiesJson: null,
  createdTime: '2026-01-01T00:00:00Z',
  updatedTime: '2026-01-01T00:00:00Z',
};

function renderForm(initial?: SqlConnection) {
  const onClose = vi.fn();
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <ChakraProvider>
      <QueryClientProvider client={queryClient}>
        <ConnectionForm isOpen onClose={onClose} initial={initial} />
      </QueryClientProvider>
    </ChakraProvider>,
  );
  return { onClose };
}

function dialectSelect(): HTMLSelectElement {
  return screen.getByRole('combobox');
}

describe('ConnectionForm', () => {
  it('offers every dialect with a human-readable label', () => {
    renderForm();
    expect(
      within(dialectSelect())
        .getAllByRole('option')
        .map((option) => option.textContent),
    ).toEqual([
      'Oracle',
      'Trino',
      'Impala',
      'MySQL',
      'MariaDB',
      'PostgreSQL',
      'MS SQL Server',
      'ClickHouse',
      'Other (generic JDBC)',
    ]);
  });

  it('shows the canonical driver class of the selected dialect as placeholder', () => {
    renderForm();
    const driver = screen.getByLabelText(/Driver class/);
    expect(driver).toHaveAttribute('placeholder', 'oracle.jdbc.OracleDriver');

    fireEvent.change(dialectSelect(), { target: { value: 'POSTGRESQL' } });
    expect(driver).toHaveAttribute('placeholder', 'org.postgresql.Driver');

    // GENERIC has no single canonical driver — no hint to mislead with
    fireEvent.change(dialectSelect(), { target: { value: 'GENERIC' } });
    expect(driver).not.toHaveAttribute('placeholder');
  });

  it('blocks submit and marks required fields when name and JDBC URL are blank', async () => {
    renderForm();
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findAllByText('Required')).toHaveLength(2);
    expect(screen.getByLabelText('JDBC URL')).toBeInvalid();
    expect(createConnection).not.toHaveBeenCalled();
  });

  it('rejects driver properties that are not a JSON object', async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'demo' } });
    fireEvent.change(screen.getByLabelText('JDBC URL'), {
      target: { value: 'jdbc:h2:mem:demo' },
    });
    fireEvent.change(screen.getByLabelText(/Driver properties/), {
      target: { value: 'not json' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText('Must be a valid JSON object'),
    ).toBeInTheDocument();
    expect(createConnection).not.toHaveBeenCalled();
  });

  it('creates a connection with trimmed values and closes the dialog', async () => {
    vi.mocked(createConnection).mockResolvedValue(editedConnection);
    const { onClose } = renderForm();
    // the users feature is off here: no schema picker at all
    expect(screen.queryByLabelText('Schema')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Name'), {
      target: { value: '  demo  ' },
    });
    fireEvent.change(screen.getByLabelText('JDBC URL'), {
      target: { value: ' jdbc:h2:mem:demo ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(createConnection).toHaveBeenCalledWith({
        name: 'demo',
        dialect: 'ORACLE',
        jdbcUrl: 'jdbc:h2:mem:demo',
        driverClass: undefined,
        username: undefined,
        password: undefined,
        clearPassword: undefined,
        propertiesJson: undefined,
      }),
    );
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('prefills an existing connection and updates it, clearing the password on request', async () => {
    vi.mocked(updateConnection).mockResolvedValue(editedConnection);
    const { onClose } = renderForm(editedConnection);

    expect(dialectSelect()).toHaveValue('POSTGRESQL');
    expect(
      screen.getByText('Leave blank to keep the current one'),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: 'Remove password' }));
    expect(screen.getByLabelText('Password')).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(updateConnection).toHaveBeenCalledWith('c1', {
        name: 'demo',
        dialect: 'POSTGRESQL',
        jdbcUrl: 'jdbc:postgresql://db.example.com:5432/app',
        driverClass: undefined,
        username: 'app',
        // clearPassword wins, so no password is sent alongside it
        password: undefined,
        clearPassword: true,
        propertiesJson: undefined,
      }),
    );
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('shows the failure reason when the connection test fails', async () => {
    vi.mocked(testConnection).mockResolvedValue({
      success: false,
      productName: null,
      productVersion: null,
      error: 'No suitable driver',
    });
    renderForm(editedConnection);

    fireEvent.click(screen.getByRole('button', { name: 'Test' }));

    expect(await screen.findByText('Error: No suitable driver')).toBeInTheDocument();
    expect(testConnection).toHaveBeenCalledWith('c1');
  });
});

describe('ConnectionForm with the users feature on', () => {
  beforeEach(() => {
    usersEnabledFlag = true;
    vi.mocked(listSchemas).mockResolvedValue([
      {
        id: 's1',
        name: 'Main',
        description: null,
        createdTime: '2026-01-01T00:00:00Z',
        updatedTime: '2026-01-01T00:00:00Z',
      },
    ]);
  });
  afterEach(() => {
    usersEnabledFlag = false;
  });

  it('requires a schema before the connection can be saved', async () => {
    renderForm();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'demo' } });
    fireEvent.change(screen.getByLabelText('JDBC URL'), {
      target: { value: 'jdbc:h2:mem:demo' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('A schema is required')).toBeInTheDocument();
    expect(createConnection).not.toHaveBeenCalled();
  });

  it('sends the chosen schema on create', async () => {
    vi.mocked(createConnection).mockResolvedValue(editedConnection);
    renderForm();
    fireEvent.change(screen.getByLabelText('Name'), { target: { value: 'demo' } });
    fireEvent.change(screen.getByLabelText('JDBC URL'), {
      target: { value: 'jdbc:h2:mem:demo' },
    });
    // the schema options arrive with the /sql-schemas query
    await screen.findByText('Main');
    fireEvent.change(screen.getByLabelText('Schema'), {
      target: { value: 's1' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(createConnection).toHaveBeenCalled());
    expect(
      vi.mocked(createConnection).mock.calls[0][0].schemaId,
    ).toBe('s1');
  });
});
