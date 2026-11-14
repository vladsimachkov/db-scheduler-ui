import { defineConfig } from '@playwright/test';

/**
 * Functional tests drive a real example-app started with the `users` profile against a
 * fresh in-memory database, e.g.:
 *
 *   (cd example-app && ../mvnw spring-boot:run -Dspring-boot.run.profiles=users \
 *     -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:mem:playwright;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
 */
export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  workers: 1,
  reporter: [['list']],
  use: {
    baseURL: process.env.UI_BASE_URL ?? 'http://localhost:8081',
    locale: 'en-US',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
});
