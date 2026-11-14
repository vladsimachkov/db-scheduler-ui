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
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import React from 'react';
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { FrontPage } from 'src/pages/FrontPage';
import i18n from 'src/i18n';

const { authState } = vi.hoisted(() => ({
  authState: {
    usersEnabled: true,
    authenticated: true,
    loading: false,
    admin: false,
    username: 'root',
    login: async () => undefined,
    logout: async () => undefined,
  },
}));

vi.mock('src/hooks/useAuth', () => ({ useAuth: () => authState }));
vi.mock('src/utils/config', () => ({
  getShowHistory: () => false,
  getShowOverview: () => false,
  getShowSql: () => true,
  getReadonly: () => false,
  getUsersEnabled: () => authState.usersEnabled,
}));

// stub every page so the guard test does not drag in the whole app
vi.mock('src/components/scheduled/TaskList', () => ({ default: () => 'tasks' }));
vi.mock('src/pages/OverviewPage', () => ({ OverviewPage: () => 'overview' }));
vi.mock('src/pages/SqlConnectionsPage', () => ({ default: () => 'connections' }));
vi.mock('src/pages/SqlJobExecutionsPage', () => ({ default: () => 'executions' }));
vi.mock('src/pages/SqlJobsPage', () => ({ default: () => 'sql-jobs' }));
vi.mock('src/components/history/LogList', () => ({ LogList: () => 'logs' }));
vi.mock('src/pages/LoginPage', () => ({ LoginPage: () => 'login' }));
vi.mock('src/pages/AdminUsersPage', () => ({ default: () => 'admin-users' }));
vi.mock('src/pages/AdminSchemasPage', () => ({ default: () => 'admin-schemas' }));

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
  authState.usersEnabled = true;
  authState.authenticated = true;
  authState.loading = false;
  authState.admin = false;
});

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <FrontPage />
    </MemoryRouter>,
  );
}

describe('FrontPage admin routes', () => {
  it('shows the admin page to an admin', async () => {
    authState.admin = true;
    renderAt('/admin/users');
    expect(await screen.findByText('admin-users')).toBeInTheDocument();
  });

  it('redirects a non-admin away from /admin/*', async () => {
    renderAt('/admin/users');
    // falls back to the index route (TaskList stub) instead of painting the admin page
    expect(await screen.findByText('tasks')).toBeInTheDocument();
    expect(screen.queryByText('admin-users')).not.toBeInTheDocument();
  });

  it('hides admin routes and nav when the users feature is off', async () => {
    authState.usersEnabled = false;
    authState.admin = true;
    renderAt('/admin/users');
    // the admin routes are not even registered when the feature is off
    expect(screen.queryByText('admin-users')).not.toBeInTheDocument();
    expect(screen.queryByText('Users')).not.toBeInTheDocument();
  });
});
