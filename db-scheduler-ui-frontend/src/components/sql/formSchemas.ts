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
import { z } from 'zod';

export const connectionFormSchema = (requireSchema = false) =>
  z
  .object({
    name: z.string().trim().min(1, 'required').max(100, 'tooLong'),
    schemaId: z.string(),
    dialect: z.enum([
      'ORACLE',
      'TRINO',
      'IMPALA',
      'MYSQL',
      'MARIADB',
      'POSTGRESQL',
      'MSSQL',
      'CLICKHOUSE',
      'GENERIC',
    ]),
    jdbcUrl: z.string(),
    driverClass: z.string().max(300, 'tooLong'),
    username: z.string().max(300, 'tooLong'),
    password: z.string().max(1000, 'tooLong'),
    clearPassword: z.boolean(),
    propertiesJson: z.string().max(4000, 'tooLong'),
  })
  .superRefine((values, ctx) => {
    if (requireSchema && !values.schemaId) {
      ctx.addIssue({
        code: 'custom',
        path: ['schemaId'],
        message: 'schemaRequired',
      });
    }
    if (!values.jdbcUrl.trim()) {
      ctx.addIssue({
        code: 'custom',
        path: ['jdbcUrl'],
        message: 'required',
      });
    }
    if (values.propertiesJson.trim()) {
      try {
        const parsed: unknown = JSON.parse(values.propertiesJson);
        if (
          typeof parsed !== 'object' ||
          parsed === null ||
          Array.isArray(parsed)
        ) {
          ctx.addIssue({
            code: 'custom',
            path: ['propertiesJson'],
            message: 'invalidJson',
          });
        }
      } catch {
        ctx.addIssue({
          code: 'custom',
          path: ['propertiesJson'],
          message: 'invalidJson',
        });
      }
    }
  });

export type ConnectionFormValues = z.infer<
  ReturnType<typeof connectionFormSchema>
>;

export const sqlJobFormSchema = (requireSchema = false) =>
  z
  .object({
    name: z.string().trim().min(1, 'required').max(100, 'tooLong'),
    schemaId: z.string(),
    description: z.string().max(1000, 'tooLong'),
    connectionId: z.string().min(1, 'connectionRequired'),
    scheduleType: z.enum(['cron', 'once']),
    cronExpression: z.string().max(100, 'tooLong'),
    runAt: z.instanceof(Date).nullable(),
    // objects, not bare strings: react-hook-form's useFieldArray only paths arrays of objects
    scripts: z
      .array(z.object({ script: z.string().trim().min(1, 'scriptRequired') }))
      .min(1),
    timeoutSeconds: z.string().trim(),
  })
  .superRefine((values, ctx) => {
    if (requireSchema && !values.schemaId) {
      ctx.addIssue({
        code: 'custom',
        path: ['schemaId'],
        message: 'schemaRequired',
      });
    }
    if (values.scheduleType === 'cron' && !values.cronExpression.trim()) {
      ctx.addIssue({
        code: 'custom',
        path: ['cronExpression'],
        message: 'required',
      });
    }
    if (values.scheduleType === 'once' && !values.runAt) {
      ctx.addIssue({
        code: 'custom',
        path: ['runAt'],
        message: 'runAtRequired',
      });
    }
    const timeout = Number(values.timeoutSeconds);
    if (
      !/^\d+$/.test(values.timeoutSeconds) ||
      timeout < 1 ||
      timeout > 86400
    ) {
      ctx.addIssue({
        code: 'custom',
        path: ['timeoutSeconds'],
        message: 'timeoutInvalid',
      });
    }
  });

export type SqlJobFormValues = z.infer<ReturnType<typeof sqlJobFormSchema>>;

/**
 * Schema validation messages are i18n keys, but zod can still emit its own built-in
 * English sentences (e.g. enum mismatches). Only translate known keys; anything else
 * falls back to the field's default key instead of leaking a raw zod message.
 */
const I18N_KEYS = new Set([
  'required',
  'tooLong',
  'timeoutInvalid',
  'runAtRequired',
  'connectionRequired',
  'scriptRequired',
  'invalidJson',
  'schemaRequired',
]);

export function formErrorText(
  t: (key: string) => string,
  message: string | undefined,
  fallbackKey: string,
): string {
  return t(message && I18N_KEYS.has(message) ? message : fallbackKey);
}
