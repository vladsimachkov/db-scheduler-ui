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
  buildJdbcUrl,
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
  dialect: 'ORACLE' as const,
  guided: true,
  host: 'db.example.com',
  port: '1521',
  database: 'ORCL',
  jdbcUrl: '',
  driverClass: '',
  username: '',
  password: '',
  clearPassword: false,
  propertiesJson: '',
};

const baseJob = {
  name: 'nightly',
  description: '',
  connectionId: 'c1',
  scheduleType: 'cron' as const,
  cronExpression: '0 0 3 * * ?',
  zoneId: '',
  runAt: null,
  script: 'SELECT 1;',
  timeoutSeconds: '300',
};

describe('buildJdbcUrl', () => {
  it('builds Oracle thin service-name URLs', () => {
    expect(
      buildJdbcUrl({
        dialect: 'ORACLE',
        host: 'db',
        port: '1521',
        database: 'ORCLPDB1',
        jdbcUrl: '',
      }),
    ).toBe('jdbc:oracle:thin:@//db:1521/ORCLPDB1');
  });

  it('omits empty port and database segments for Trino and Impala', () => {
    expect(
      buildJdbcUrl({ dialect: 'TRINO', host: 'trino', port: '', database: '', jdbcUrl: '' }),
    ).toBe('jdbc:trino://trino');
    expect(
      buildJdbcUrl({
        dialect: 'IMPALA',
        host: 'impala',
        port: '21050',
        database: 'default',
        jdbcUrl: '',
      }),
    ).toBe('jdbc:impala://impala:21050/default');
  });

  it('builds MySQL URLs and passes GENERIC through', () => {
    expect(
      buildJdbcUrl({ dialect: 'MYSQL', host: 'mysql', port: '3306', database: 'app', jdbcUrl: '' }),
    ).toBe('jdbc:mysql://mysql:3306/app');
    expect(
      buildJdbcUrl({
        dialect: 'GENERIC',
        host: '',
        port: '',
        database: '',
        jdbcUrl: ' jdbc:h2:mem:demo ',
      }),
    ).toBe('jdbc:h2:mem:demo');
  });
});

describe('connectionFormSchema', () => {
  it('accepts a valid guided connection', () => {
    expect(connectionFormSchema.safeParse(baseConnection).success).toBe(true);
  });

  it('requires host in guided mode and database for Oracle/MySQL', () => {
    const result = connectionFormSchema.safeParse({ ...baseConnection, host: ' ' });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'host')).toContain('required');

    const noDb = connectionFormSchema.safeParse({ ...baseConnection, database: '' });
    expect(noDb.success).toBe(false);
    expect(messagesFor(noDb.error, 'database')).toContain('required');
  });

  it('requires jdbcUrl in raw mode', () => {
    const result = connectionFormSchema.safeParse({
      ...baseConnection,
      guided: false,
      jdbcUrl: '  ',
    });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'jdbcUrl')).toContain('required');
  });

  it('validates propertiesJson as a JSON object', () => {
    expect(
      connectionFormSchema.safeParse({ ...baseConnection, propertiesJson: '{"a":"b"}' }).success,
    ).toBe(true);
    for (const bad of ['not json', '[1,2]', 'null']) {
      const result = connectionFormSchema.safeParse({
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
    expect(sqlJobFormSchema.safeParse(baseJob).success).toBe(true);
  });

  it('requires cronExpression for cron jobs', () => {
    const result = sqlJobFormSchema.safeParse({ ...baseJob, cronExpression: ' ' });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'cronExpression')).toContain('required');
  });

  it('requires runAt for one-time jobs', () => {
    const result = sqlJobFormSchema.safeParse({ ...baseJob, scheduleType: 'once' });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'runAt')).toContain('runAtRequired');

    const withDate = sqlJobFormSchema.safeParse({
      ...baseJob,
      scheduleType: 'once',
      cronExpression: '',
      runAt: new Date(),
    });
    expect(withDate.success).toBe(true);
  });

  it('bounds timeoutSeconds to 1..86400', () => {
    for (const bad of ['0', '86401', 'abc', '-5', '1.5']) {
      const result = sqlJobFormSchema.safeParse({ ...baseJob, timeoutSeconds: bad });
      expect(result.success).toBe(false);
      expect(messagesFor(result.error, 'timeoutSeconds')).toContain('timeoutInvalid');
    }
  });

  it('requires a non-blank script', () => {
    const result = sqlJobFormSchema.safeParse({ ...baseJob, script: '   ' });
    expect(result.success).toBe(false);
    expect(messagesFor(result.error, 'script')).toContain('scriptRequired');
  });
});
