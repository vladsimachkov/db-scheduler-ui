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
