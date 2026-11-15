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
import { fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { HeaderBar } from 'src/components/common/HeaderBar';
import i18n from 'src/i18n';
import { Schema } from 'src/models/Schema';

vi.mock('src/services/pollLogs', () => ({
  POLL_LOGS_QUERY_KEY: 'logs/poll',
  pollLogs: vi.fn(),
}));
vi.mock('src/services/pollTasks', () => ({
  POLL_TASKS_QUERY_KEY: 'tasks/poll',
  pollTasks: vi.fn(),
}));

const schema = (id: string, name: string): Schema => ({
  id,
  name,
  description: null,
  createdTime: '2026-01-01T00:00:00Z',
  updatedTime: '2026-01-01T00:00:00Z',
});

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

function renderBar(props: Partial<React.ComponentProps<typeof HeaderBar>> = {}) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <ChakraProvider>
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <HeaderBar
            title="All tasks"
            params={{ filter: 'all' as never }}
            setCurrentFilter={vi.fn()}
            setSearchTermTaskName={vi.fn()}
            setSearchTermTaskInstance={vi.fn()}
            setTaskNameExactMatch={vi.fn()}
            setTaskInstanceExactMatch={vi.fn()}
            taskNameExactMatch={false}
            taskInstanceExactMatch={false}
            {...props}
          />
        </MemoryRouter>
      </QueryClientProvider>
    </ChakraProvider>,
  );
}

describe('schema filter', () => {
  it('renders the picker with an All schemas option when several schemas are accessible', () => {
    renderBar({
      schemaOptions: [schema('s1', 'Main'), schema('s2', 'Reports')],
      schemaId: '',
      onSchemaIdChange: vi.fn(),
    });

    const picker = screen.getByRole('combobox', { name: 'Schema' });
    expect(picker).toHaveValue('');
    expect(
      screen.getByRole('option', { name: 'All schemas' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Reports' })).toBeInTheDocument();
  });

  it('reports the picked schema', () => {
    const onSchemaIdChange = vi.fn();
    renderBar({
      schemaOptions: [schema('s1', 'Main'), schema('s2', 'Reports')],
      schemaId: 's1',
      onSchemaIdChange,
    });

    fireEvent.change(screen.getByRole('combobox', { name: 'Schema' }), {
      target: { value: 's2' },
    });

    expect(onSchemaIdChange).toHaveBeenCalledWith('s2');
  });

  it('hides the picker for a single schema — nothing to choose', () => {
    renderBar({
      schemaOptions: [schema('s1', 'Main')],
      schemaId: 's1',
      onSchemaIdChange: vi.fn(),
    });

    expect(
      screen.queryByRole('combobox', { name: 'Schema' }),
    ).not.toBeInTheDocument();
  });

  it('hides the picker when the users feature is off', () => {
    renderBar();

    expect(
      screen.queryByRole('combobox', { name: 'Schema' }),
    ).not.toBeInTheDocument();
  });
});
