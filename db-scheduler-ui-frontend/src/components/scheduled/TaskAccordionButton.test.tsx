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
import { Accordion, AccordionItem, ChakraProvider } from '@chakra-ui/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { MemoryRouter } from 'react-router-dom';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import { TaskAccordionButton } from 'src/components/scheduled/TaskAccordionButton';
import i18n from 'src/i18n';
import { Task, TaskDisplay } from 'src/models/Task';
import { Schema } from 'src/models/Schema';

const schema = (id: string, name: string): Schema => ({
  id,
  name,
  description: null,
  createdTime: '2026-01-01T00:00:00Z',
  updatedTime: '2026-01-01T00:00:00Z',
});

const task = (overrides: Partial<Task> = {}): Task => ({
  taskName: 'sql-script-task',
  taskInstance: ['550e8400-e29b-41d4-a716-446655440000'],
  taskData: [],
  executionTime: [new Date('2026-01-01T00:00:00Z')],
  picked: false,
  pickedBy: [''],
  lastSuccess: [],
  lastFailure: null,
  consecutiveFailures: [0],
  lastHeartbeat: null,
  version: 1,
  ...overrides,
});

const renderButton = (props: Partial<Task> & { schemas?: Schema[]; showSchema?: boolean } = {}) =>
  render(
    <ChakraProvider>
      <QueryClientProvider client={new QueryClient()}>
        <MemoryRouter>
          <Accordion>
            <AccordionItem>
              <TaskAccordionButton {...task(props)} refetch={vi.fn()} />
            </AccordionItem>
          </Accordion>
        </MemoryRouter>
      </QueryClientProvider>
    </ChakraProvider>,
  );

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

describe('TaskAccordionButton display name', () => {
  it('shows the resolved display name instead of the raw instance id', () => {
    const displays: (TaskDisplay | null)[] = [
      { displayName: 'nightly', schemaId: 's1' },
    ];
    renderButton({ displays });

    expect(screen.getByText('nightly')).toBeInTheDocument();
    expect(
      screen.queryByText('550e8400-e29b-41d4-a716-446655440000'),
    ).not.toBeInTheDocument();
  });

  it('falls back to the instance id without display info', () => {
    renderButton();

    expect(
      screen.getByText('550e8400-e29b-41d4-a716-446655440000'),
    ).toBeInTheDocument();
  });

  it('falls back when no resolver recognized the execution (null hole)', () => {
    renderButton({ displays: [null] });

    expect(
      screen.getByText('550e8400-e29b-41d4-a716-446655440000'),
    ).toBeInTheDocument();
  });

  it('truncates a long display name like the instance id before it', () => {
    const longName = 'x'.repeat(50);
    renderButton({ displays: [{ displayName: longName, schemaId: null }] });

    expect(screen.getByText(`${'x'.repeat(40)}...`)).toBeInTheDocument();
  });
});

describe('TaskAccordionButton schema column', () => {
  it('shows the schema name for the resolved schema id', () => {
    renderButton({
      displays: [{ displayName: 'nightly', schemaId: 's1' }],
      schemas: [schema('s1', 'Main'), schema('s2', 'Reports')],
      showSchema: true,
    });

    expect(screen.getByText('Main')).toBeInTheDocument();
  });

  it('shows a dash when the display has no schema', () => {
    renderButton({
      displays: [{ displayName: 'nightly', schemaId: null }],
      schemas: [schema('s1', 'Main')],
      showSchema: true,
    });

    expect(screen.getByText('—')).toBeInTheDocument();
  });

  it('renders no schema cell when the column is off', () => {
    renderButton({
      displays: [{ displayName: 'nightly', schemaId: 's1' }],
      schemas: [schema('s1', 'Main')],
      showSchema: false,
    });

    expect(screen.queryByText('Main')).not.toBeInTheDocument();
  });
});
