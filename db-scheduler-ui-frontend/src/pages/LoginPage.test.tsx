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
import { AuthProvider } from 'src/hooks/AuthProvider';
import i18n from 'src/i18n';
import LoginPage from 'src/pages/LoginPage';
import { ApiError } from 'src/services/apiClient';
import { login } from 'src/services/auth';

vi.mock('src/services/auth', async (importOriginal) => ({
  ...(await importOriginal<object>()),
  login: vi.fn(),
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

function renderLogin() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <ChakraProvider>
      <QueryClientProvider client={queryClient}>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </QueryClientProvider>
    </ChakraProvider>,
  );
}

async function submit() {
  // Chakra appends a required-indicator "*" to the label text, so match the prefix
  fireEvent.change(screen.getByLabelText(/^Username/), {
    target: { value: 'bob' },
  });
  fireEvent.change(screen.getByLabelText(/^Password/), {
    target: { value: 'secret' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Sign in' }));
}

describe('LoginPage', () => {
  it('submits the entered credentials', async () => {
    vi.mocked(login).mockResolvedValue({
      authenticated: true,
      username: 'bob',
      admin: false,
    });

    renderLogin();
    await submit();

    await waitFor(() =>
      expect(login).toHaveBeenCalledWith('bob', 'secret'),
    );
  });

  it('shows the backend message when the credentials are wrong', async () => {
    vi.mocked(login).mockRejectedValue(new ApiError(401, 'Invalid credentials'));

    renderLogin();
    await submit();

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Invalid credentials'),
    );
  });

  it('shows a generic error when the request fails without a message', async () => {
    vi.mocked(login).mockRejectedValue(new Error(''));

    renderLogin();
    await submit();

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Login failed'),
    );
  });
});
