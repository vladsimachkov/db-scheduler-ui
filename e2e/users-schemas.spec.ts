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
import { expect, Page, test } from '@playwright/test';

/**
 * Functional walk of the users & schemas feature through the real UI:
 * login gate, admin wiring up two schemas + user + grant, the schema pickers the
 * admin gets with 2+ schemas, the granted user working inside the single schema
 * with every schema picker hidden and auto-filled, and the schema vanishing for
 * that user once the grant is revoked.
 *
 * Requires a running example-app on the `users` profile with a fresh database
 * (bootstrap admin/admin), see playwright.config.ts.
 */

const ADMIN = { username: 'admin', password: 'admin' };
const BOB = { username: 'bob', password: 'bob-secret-42' };

async function signIn(page: Page, { username, password }: { username: string; password: string }) {
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page.getByText(`Signed in as ${username}`)).toBeVisible();
}

async function signOut(page: Page) {
  await page.getByRole('button', { name: 'Sign out' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
}

/** One row of the schemas table, so per-schema buttons stay unambiguous. */
function schemaRow(page: Page, name: string) {
  return page.getByRole('row').filter({ has: page.getByRole('cell', { name, exact: true }) });
}

test.describe.serial('users and schemas', () => {
  test('admin logs in and wires up schemas, user and grant', async ({ page }) => {
    await page.goto('/db-scheduler');

    // the login gate replaces the dashboard
    await expect(page.getByRole('heading', { name: 'Sign in' })).toBeVisible();
    await page.getByLabel('Username').fill(ADMIN.username);
    await page.getByLabel('Password').fill('wrong-password');
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByRole('alert')).toContainText('Login failed');

    await signIn(page, ADMIN);
    // admin-only navigation appeared
    await expect(page.getByRole('button', { name: 'Users' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Schemas' })).toBeVisible();

    // --- schemas: two of them, so the admin keeps the schema pickers ---
    await page.getByRole('button', { name: 'Schemas' }).click();
    await page.getByRole('button', { name: 'Add schema' }).click();
    await page.getByLabel(/^Name/).fill('Main');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'Main' })).toBeVisible();
    await page.getByRole('button', { name: 'Add schema' }).click();
    await page.getByLabel(/^Name/).fill('Reports');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'Reports' })).toBeVisible();

    // --- user ---
    await page.getByRole('button', { name: 'Users' }).click();
    await page.getByRole('button', { name: 'Add user' }).click();
    await page.getByLabel(/^Username/).fill(BOB.username);
    await page.getByLabel(/^Password/).fill(BOB.password);
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'bob' })).toBeVisible();

    // --- grant Main -> bob (scoped to the Main row: two Access buttons exist) ---
    await page.getByRole('button', { name: 'Schemas' }).click();
    await schemaRow(page, 'Main').getByRole('button', { name: 'Access' }).click();
    await expect(page.getByText('No one has access yet')).toBeVisible();
    await page.getByRole('dialog').getByRole('combobox').selectOption(BOB.username);
    await page.getByRole('button', { name: 'Grant' }).click();
    await expect(page.getByRole('listitem').filter({ hasText: 'bob' })).toBeVisible();
    await page.getByRole('button', { name: 'Close' }).click();

    // the admin's own destructive actions are disabled (self-lockout guard)
    await page.getByRole('button', { name: 'Users' }).click();
    const selfRow = page
      .getByRole('row')
      .filter({ has: page.getByRole('cell', { name: 'admin', exact: true }) });
    await expect(selfRow.getByRole('button', { name: 'Delete' })).toBeDisabled();

    // with two accessible schemas the Scheduled and History pages offer the picker
    for (const path of ['/db-scheduler/scheduled', '/db-scheduler/history/all']) {
      await page.goto(path);
      const filter = page.getByRole('combobox', { name: 'Schema' });
      await expect(filter).toBeVisible();
      await expect(filter.locator('option')).toHaveText(['All schemas', 'Main', 'Reports']);
    }
  });

  test('granted user works inside the single schema without any schema picker', async ({
    page,
  }) => {
    await page.goto('/db-scheduler');
    await signIn(page, BOB);

    // no admin navigation for a plain user
    await expect(page.getByRole('button', { name: 'Users' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Schemas' })).toHaveCount(0);

    // bob creates a connection: one accessible schema -> no picker, Main auto-filled
    await page.getByRole('button', { name: 'Connections' }).click();
    await page.getByRole('button', { name: 'Add connection' }).click();
    await page.getByLabel('Name', { exact: true }).fill('bob-conn');
    await page.getByLabel('JDBC URL').fill('jdbc:h2:mem:whatever');
    await expect(page.getByRole('combobox', { name: 'Schema' })).toHaveCount(0);
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'bob-conn' })).toBeVisible();

    // the jobs page has no filter either — the single schema needs no choosing
    await page.getByRole('button', { name: 'SQL jobs' }).click();
    await expect(page.getByRole('combobox', { name: 'Schema' })).toHaveCount(0);

    // and neither do the Scheduled and History pages
    for (const path of ['/db-scheduler/scheduled', '/db-scheduler/history/all']) {
      await page.goto(path);
      await expect(page.getByRole('combobox', { name: 'Schema' })).toHaveCount(0);
    }

    await signOut(page);
  });

  test('scheduled page shows the job name and a schema column for multi-schema users', async ({
    page,
  }) => {
    await page.goto('/db-scheduler');
    await signIn(page, ADMIN);

    // create a sql job through the API (the script editor is CodeMirror — out of scope here);
    // the UI sign-in above already put the session cookie on the context
    // mutating endpoints demand the X-Requested-With header (CSRF guard the SPA always sends)
    const headers = { 'X-Requested-With': 'XMLHttpRequest' };
    const schemas = await (await page.request.get('/db-scheduler-api/sql-schemas')).json();
    const mainId = schemas.find((s: { name: string }) => s.name === 'Main').id;
    const connection = await page.request.post('/db-scheduler-api/sql-connections', {
      headers,
      data: {
        name: 'e2e-conn',
        dialect: 'POSTGRESQL',
        jdbcUrl: 'jdbc:h2:mem:e2e;DB_CLOSE_DELAY=-1',
        schemaId: mainId,
      },
    });
    expect(connection.ok(), await connection.text()).toBeTruthy();
    const connectionId = (await connection.json()).id;
    const job = await page.request.post('/db-scheduler-api/sql-jobs', {
      headers,
      data: {
        name: 'nightly',
        connectionId,
        schemaId: mainId,
        scripts: ['SELECT 1;'],
        cronExpression: '0 0 3 * * *',
        timeoutSeconds: 30,
      },
    });
    expect(job.ok(), await job.text()).toBeTruthy();

    // the row carries the job's own name and its schema, and the column header is there
    // (a task row is an h2 wrapping the accordion button, not a button itself)
    await page.goto('/db-scheduler/scheduled');
    await expect(page.getByText('Schema', { exact: true })).toBeVisible();
    const jobRow = page.getByRole('heading', { level: 2 }).filter({ hasText: 'nightly' });
    await expect(jobRow).toContainText('Main');

    // bob reaches only one schema: same row, but no Schema column anywhere
    await signOut(page);
    await signIn(page, BOB);
    await page.goto('/db-scheduler/scheduled');
    await expect(page.getByText('Schema', { exact: true })).toHaveCount(0);
    await expect(
      page.getByRole('heading', { level: 2 }).filter({ hasText: 'nightly' }),
    ).toBeVisible();

    await signOut(page);
  });

  test('revoking the grant hides the schema and its contents from the user', async ({ page }) => {
    // admin revokes
    await page.goto('/db-scheduler');
    await signIn(page, ADMIN);
    await page.getByRole('button', { name: 'Schemas' }).click();
    await schemaRow(page, 'Main').getByRole('button', { name: 'Access' }).click();
    await page.getByRole('button', { name: 'Revoke' }).click();
    await expect(page.getByText('No one has access yet')).toBeVisible();
    await page.getByRole('button', { name: 'Close' }).click();
    await signOut(page);

    // bob: the schema is gone — and with it his own connection, without any re-login
    await page.getByLabel('Username').fill(BOB.username);
    await page.getByLabel('Password').fill(BOB.password);
    await page.getByRole('button', { name: 'Sign in' }).click();
    await expect(page.getByText('Signed in as bob')).toBeVisible();
    await page.getByRole('button', { name: 'Connections' }).click();
    await expect(page.getByRole('cell', { name: 'bob-conn' })).toHaveCount(0);
  });
});
