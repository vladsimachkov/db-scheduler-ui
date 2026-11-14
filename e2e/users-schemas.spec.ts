import { expect, Page, test } from '@playwright/test';

/**
 * Functional walk of the users & schemas feature through the real UI:
 * login gate, admin wiring up a schema + user + grant, the granted user working
 * inside the schema, and the schema vanishing for that user once the grant is revoked.
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

test.describe.serial('users and schemas', () => {
  test('admin logs in and wires up schema, user and grant', async ({ page }) => {
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

    // --- schema ---
    await page.getByRole('button', { name: 'Schemas' }).click();
    await page.getByRole('button', { name: 'Add schema' }).click();
    await page.getByLabel(/^Name/).fill('Main');
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'Main' })).toBeVisible();

    // --- user ---
    await page.getByRole('button', { name: 'Users' }).click();
    await page.getByRole('button', { name: 'Add user' }).click();
    await page.getByLabel(/^Username/).fill(BOB.username);
    await page.getByLabel(/^Password/).fill(BOB.password);
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'bob' })).toBeVisible();

    // --- grant Main -> bob ---
    await page.getByRole('button', { name: 'Schemas' }).click();
    await page.getByRole('button', { name: 'Access' }).click();
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
  });

  test('granted user works inside the schema and cannot see the admin area', async ({ page }) => {
    await page.goto('/db-scheduler');
    await signIn(page, BOB);

    // no admin navigation for a plain user
    await expect(page.getByRole('button', { name: 'Users' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Schemas' })).toHaveCount(0);

    // bob creates a connection inside the granted schema
    await page.getByRole('button', { name: 'Connections' }).click();
    await page.getByRole('button', { name: 'Add connection' }).click();
    await page.getByLabel('Name', { exact: true }).fill('bob-conn');
    await page.getByLabel('JDBC URL').fill('jdbc:h2:mem:whatever');
    await page.getByLabel('Schema').selectOption({ label: 'Main' });
    await page.getByRole('button', { name: 'Save' }).click();
    await expect(page.getByRole('cell', { name: 'bob-conn' })).toBeVisible();

    // the schema filter on the jobs page offers exactly the granted schema
    await page.getByRole('button', { name: 'SQL jobs' }).click();
    const filter = page.getByRole('combobox', { name: 'Schema' });
    await expect(filter).toBeVisible();
    await expect(filter).toContainText('Main');

    await signOut(page);
  });

  test('revoking the grant hides the schema and its contents from the user', async ({ page }) => {
    // admin revokes
    await page.goto('/db-scheduler');
    await signIn(page, ADMIN);
    await page.getByRole('button', { name: 'Schemas' }).click();
    await page.getByRole('button', { name: 'Access' }).click();
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
