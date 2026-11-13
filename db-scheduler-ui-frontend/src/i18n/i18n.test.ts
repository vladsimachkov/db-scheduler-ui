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
import { beforeEach, describe, expect, it } from 'vitest';
import i18n from 'src/i18n';

describe('i18n', () => {
  beforeEach(async () => {
    await i18n.changeLanguage('ru');
  });

  it('renders Russian translations by default', () => {
    expect(i18n.t('overview:title')).toBe('Все задачи');
    expect(i18n.t('common:nav.history')).toBe('История');
  });

  it('applies Russian plural categories', () => {
    expect(i18n.t('overview:instances', { count: 1 })).toBe('1 экземпляр');
    expect(i18n.t('overview:instances', { count: 3 })).toBe('3 экземпляра');
    expect(i18n.t('overview:instances', { count: 5 })).toBe('5 экземпляров');
    expect(i18n.t('overview:instances', { count: 21 })).toBe('21 экземпляр');
  });

  it('renders the summary-strip showing counter with Russian plurals', () => {
    // Regression: ICU-style {total, plural, ...} is NOT supported by plain i18next
    // and rendered as garbage ("Показано 1 из 5 5}"). Must use _one/_few/_many keys.
    expect(i18n.t('overview:strip.showing', { shown: 1, count: 5 })).toBe(
      'Показано 1 из 5 задач',
    );
    expect(i18n.t('overview:strip.showing', { shown: 1, count: 1 })).toBe(
      'Показано 1 из 1 задачи',
    );
    expect(i18n.t('overview:strip.showing', { shown: 2, count: 3 })).toBe(
      'Показано 2 из 3 задач',
    );
  });

  it('falls back to English when switched', async () => {
    await i18n.changeLanguage('en');
    expect(i18n.t('overview:title')).toBe('All tasks');
    expect(i18n.t('overview:instances', { count: 2 })).toBe('2 instances');
    expect(i18n.t('overview:strip.showing', { shown: 1, count: 5 })).toBe(
      'Showing 1 of 5 tasks',
    );
    expect(i18n.t('overview:strip.showing', { shown: 1, count: 1 })).toBe(
      'Showing 1 of 1 task',
    );
  });

  it('falls back to English for unsupported languages', async () => {
    await i18n.changeLanguage('de');
    expect(i18n.t('overview:title')).toBe('All tasks');
  });
});
