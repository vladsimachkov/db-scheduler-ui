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
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from 'src/hooks/AuthProvider';
import { useAuth } from 'src/hooks/useAuth';
import { UNAUTHORIZED_EVENT } from 'src/services/apiClient';
import { getMe, login, logout } from 'src/services/auth';

vi.mock('src/utils/config', () => ({ getUsersEnabled: () => true }));
vi.mock('src/services/auth', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  getMe: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
}));

beforeEach(() => {
  vi.resetAllMocks();
});

const Probe: React.FC = () => {
  const { authenticated, username, login: doLogin, logout: doLogout } = useAuth();
  return (
    <div>
      <span data-testid="state">
        {authenticated ? `on:${username}` : 'off'}
      </span>
      <button onClick={() => void doLogin('bob', 'pw')}>login</button>
      <button onClick={() => void doLogout()}>logout</button>
    </div>
  );
};

function renderProbe() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('AuthProvider', () => {
  it('authenticates from /auth/me and drops back to anonymous on the 401 event', async () => {
    vi.mocked(getMe).mockResolvedValue({
      authenticated: true,
      username: 'bob',
      admin: false,
    });

    renderProbe();
    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('on:bob'),
    );

    window.dispatchEvent(new Event(UNAUTHORIZED_EVENT));
    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('off'),
    );
  });

  it('login() stores the returned user, logout() clears it', async () => {
    vi.mocked(getMe).mockResolvedValue({
      authenticated: false,
      username: null,
      admin: false,
    });
    vi.mocked(login).mockResolvedValue({
      authenticated: true,
      username: 'bob',
      admin: true,
    });
    vi.mocked(logout).mockResolvedValue(undefined);

    renderProbe();
    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('off'),
    );

    fireEvent.click(screen.getByText('login'));
    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('on:bob'),
    );

    fireEvent.click(screen.getByText('logout'));
    await waitFor(() =>
      expect(screen.getByTestId('state')).toHaveTextContent('off'),
    );
  });
});
