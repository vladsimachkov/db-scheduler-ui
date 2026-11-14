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
import { describe, expect, it } from 'vitest';
import { ZodError } from 'zod';
import {
  connectionFormSchema,
  sqlJobFormSchema,
} from 'src/components/sql/formSchemas';

function messagesFor(error: unknown, path: string): string[] {
  expect(error).toBeInstanceOf(ZodError);
  return (error as ZodError).issues
    .filter((issue) => issue.path[0] === path)
    .map((issue) => issue.message);
}

const baseConnection = {
  name: 'demo',
  schemaId: '',
  dialect: 'ORACLE' as const,
  jdbcUrl: 'jdbc:oracle:thin:@//db.example.com:1521/ORCL',
  driverClass: '',
  username: '',
  password: '',
  clearPassword: false,
  propertiesJson: '',
};

const baseJob = {
  name: 'nightly',
  schemaId: '',
  description: '',
  connectionId: 'c1',
  scheduleType: 'cron' as const,
  cronExpression: '0 0 3 * * ?',
  runAt: null,
  scripts: [{ script: 'SELECT 1;' }],
  timeoutSeconds: '300',
};

describe('connectionFormSchema', () => {
  it('accepts a valid connection', () => {
    expect(connectionFormSchema().safeParse(baseConnection).success).toBe(true);
  });

  it('requires jdbcUrl', () => {
    const result = connectionFormSchema().safeParse({
      ...baseConnection,
      jdbcUrl: '  ',
    });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'jdbcUrl')).toContain('required');
  });

  it('validates propertiesJson as a JSON object', () => {
    expect(
      connectionFormSchema().safeParse({ ...baseConnection, propertiesJson: '{"a":"b"}' }).success,
    ).toBe(true);
    for (const bad of ['not json', '[1,2]', 'null']) {
      const result = connectionFormSchema().safeParse({
        ...baseConnection,
        propertiesJson: bad,
      });
      expect(result.success).toBe(false);
      expect(messagesFor(result.error, 'propertiesJson')).toContain('invalidJson');
    }
  });
});

describe('sqlJobFormSchema', () => {
  it('accepts a valid cron job', () => {
    expect(sqlJobFormSchema().safeParse(baseJob).success).toBe(true);
  });

  it('requires cronExpression for cron jobs', () => {
    const result = sqlJobFormSchema().safeParse({ ...baseJob, cronExpression: ' ' });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'cronExpression')).toContain('required');
  });

  it('requires runAt for one-time jobs', () => {
    const result = sqlJobFormSchema().safeParse({ ...baseJob, scheduleType: 'once' });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'runAt')).toContain('runAtRequired');

    const withDate = sqlJobFormSchema().safeParse({
      ...baseJob,
      scheduleType: 'once',
      cronExpression: '',
      runAt: new Date(),
    });
    expect(withDate.success).toBe(true);
  });

  it('bounds timeoutSeconds to 1..86400', () => {
    for (const bad of ['0', '86401', 'abc', '-5', '1.5']) {
      const result = sqlJobFormSchema().safeParse({ ...baseJob, timeoutSeconds: bad });
      expect(result.success).toBe(false);
      expect(messagesFor(result.error, 'timeoutSeconds')).toContain('timeoutInvalid');
    }
  });

  it('requires a non-blank script in every list entry', () => {
    const blank = sqlJobFormSchema().safeParse({
      ...baseJob,
      scripts: [{ script: 'SELECT 1;' }, { script: '  ' }],
    });
    expect(blank.success).toBe(false);
    expect(messagesFor(blank.error, 'scripts')).toContain('scriptRequired');

    const empty = sqlJobFormSchema().safeParse({ ...baseJob, scripts: [] });
    expect(empty.success).toBe(false);
  });
});

describe('schema requirement (users feature on)', () => {
  it('requires schemaId only when the feature demands it', () => {
    expect(connectionFormSchema(true).safeParse(baseConnection).success).toBe(false);
    expect(
      messagesFor(
        connectionFormSchema(true).safeParse(baseConnection).error,
        'schemaId',
      ),
    ).toContain('schemaRequired');
    expect(
      connectionFormSchema(true).safeParse({ ...baseConnection, schemaId: 's1' })
        .success,
    ).toBe(true);
    expect(sqlJobFormSchema(true).safeParse(baseJob).success).toBe(false);
    expect(
      sqlJobFormSchema(true).safeParse({ ...baseJob, schemaId: 's1' }).success,
    ).toBe(true);
  });
});
