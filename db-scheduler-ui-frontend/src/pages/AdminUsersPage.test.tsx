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
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import AdminUsersPage from 'src/pages/AdminUsersPage';
import i18n from 'src/i18n';
import { User } from 'src/models/User';
import {
  createUser,
  deleteUser,
  listUsers,
  resetPassword,
  updateUser,
} from 'src/services/users';

// the page mirrors the backend's self-lockout guards against the signed-in user
vi.mock('src/hooks/useAuth', () => ({ useAuth: () => ({ username: 'root' }) }));

// config.ts fails safe to readOnly when /config is unreachable — in tests that hides every button
vi.mock('src/utils/config', () => ({ getReadonly: () => false }));
vi.mock('src/services/users', () => ({
  USERS_QUERY_KEY: 'users',
  listUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetPassword: vi.fn(),
  deleteUser: vi.fn(),
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

const admin: User = {
  id: 'u1',
  username: 'root',
  admin: true,
  enabled: true,
  createdTime: '2026-01-01T00:00:00Z',
  updatedTime: '2026-01-01T00:00:00Z',
};
const plain: User = {
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
        <AdminUsersPage />
      </QueryClientProvider>
    </ChakraProvider>,
  );
}

describe('AdminUsersPage', () => {
  it('lists users with their flags', async () => {
    vi.mocked(listUsers).mockResolvedValue([admin, plain]);
    renderPage();
    expect(await screen.findByText('root')).toBeInTheDocument();
    expect(screen.getByText('bob')).toBeInTheDocument();
    // bob is not an admin and the button offers to promote him
    expect(screen.getByRole('button', { name: 'Make admin' })).toBeInTheDocument();
  });

  it('creates a user from the modal', async () => {
    vi.mocked(listUsers).mockResolvedValue([admin]);
    vi.mocked(createUser).mockResolvedValue(plain);
    renderPage();

    fireEvent.click(screen.getByRole('button', { name: 'Add user' }));
    fireEvent.change(await screen.findByLabelText(/^Username/), {
      target: { value: 'bob' },
    });
    fireEvent.change(screen.getByLabelText(/^Password/), {
      target: { value: 'secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(createUser).toHaveBeenCalledWith({ username: 'bob', password: 'secret' }),
    );
  });

  it('toggles a flag through update', async () => {
    vi.mocked(listUsers).mockResolvedValue([admin, plain]);
    vi.mocked(updateUser).mockResolvedValue(plain);
    renderPage();
    await screen.findByText('bob');

    fireEvent.click(screen.getByRole('button', { name: 'Make admin' }));
    await waitFor(() =>
      expect(updateUser).toHaveBeenCalledWith('u2', { admin: true }),
    );
  });

  it('confirms before deleting', async () => {
    vi.mocked(listUsers).mockResolvedValue([admin, plain]);
    vi.mocked(deleteUser).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('bob');

    fireEvent.click(within(rowOf('bob')).getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText('Delete user bob?')).toBeInTheDocument();
    // the dialog's confirm button is the last one rendered
    const buttons = screen.getAllByRole('button', { name: 'Delete' });
    fireEvent.click(buttons[buttons.length - 1]);

    await waitFor(() => expect(deleteUser).toHaveBeenCalledWith('u2'));
  });

  it('disables self-management on the signed-in row', async () => {
    vi.mocked(listUsers).mockResolvedValue([admin, plain]);
    renderPage();
    await screen.findByText('root');

    // the backend rejects self-demote/self-disable/self-delete; the UI must not offer them
    const selfRow = rowOf('root');
    expect(within(selfRow).getByRole('button', { name: 'Revoke admin' })).toBeDisabled();
    expect(within(selfRow).getByRole('button', { name: 'Disable' })).toBeDisabled();
    expect(within(selfRow).getByRole('button', { name: 'Delete' })).toBeDisabled();
    // bob's row stays fully actionable
    expect(within(rowOf('bob')).getByRole('button', { name: 'Delete' })).toBeEnabled();
  });

  it('resets a password from the modal', async () => {
    vi.mocked(listUsers).mockResolvedValue([admin, plain]);
    vi.mocked(resetPassword).mockResolvedValue(undefined);
    renderPage();
    await screen.findByText('bob');

    fireEvent.click(within(rowOf('bob')).getByRole('button', { name: 'Reset password' }));
    fireEvent.change(await screen.findByLabelText(/^Password/), {
      target: { value: 'new-secret' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(resetPassword).toHaveBeenCalledWith('u2', 'new-secret'));
  });
});

function rowOf(username: string): HTMLElement {
  return screen.getByText(username).closest('tr') as HTMLElement;
}
