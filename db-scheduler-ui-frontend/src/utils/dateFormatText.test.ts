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
import { describe, expect, it, beforeAll } from 'vitest';
import i18n from 'src/i18n';
import { dateFormatText } from 'src/utils/dateFormatText';

describe('dateFormatText', () => {
  const date = new Date('2026-11-13T14:05:09');

  beforeAll(async () => {
    await i18n.changeLanguage('ru');
  });

  it('formats with the Russian locale', () => {
    expect(dateFormatText(date)).toBe('13 ноября 2026, 14:05:09');
  });

  it('formats with the English locale after switching', async () => {
    await i18n.changeLanguage('en');
    expect(dateFormatText(date)).toBe('13. Nov 26, 14:05:09');
    await i18n.changeLanguage('ru');
  });
});
