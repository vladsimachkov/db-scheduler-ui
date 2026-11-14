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
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { ru, enUS } from 'date-fns/locale';
import type { Locale } from 'date-fns';

import ruCommon from './locales/ru/common.json';
import ruOverview from './locales/ru/overview.json';
import ruScheduled from './locales/ru/scheduled.json';
import ruHistory from './locales/ru/history.json';
import ruSql from './locales/ru/sql.json';
import ruConnections from './locales/ru/connections.json';
import ruAuth from './locales/ru/auth.json';
import enCommon from './locales/en/common.json';
import enOverview from './locales/en/overview.json';
import enScheduled from './locales/en/scheduled.json';
import enHistory from './locales/en/history.json';
import enSql from './locales/en/sql.json';
import enConnections from './locales/en/connections.json';
import enAuth from './locales/en/auth.json';

export const NAMESPACES = [
  'common',
  'overview',
  'scheduled',
  'history',
  'sql',
  'connections',
  'auth',
];

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      ru: {
        common: ruCommon,
        overview: ruOverview,
        scheduled: ruScheduled,
        history: ruHistory,
        sql: ruSql,
        connections: ruConnections,
        auth: ruAuth,
      },
      en: {
        common: enCommon,
        overview: enOverview,
        scheduled: enScheduled,
        history: enHistory,
        sql: enSql,
        connections: enConnections,
        auth: enAuth,
      },
    },
    fallbackLng: 'en',
    supportedLngs: ['ru', 'en'],
    // Persisted choice wins over navigator.language; browsers asking for an unsupported
    // language fall back to English (the extraction source of truth).
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'lng',
      caches: ['localStorage'],
    },
    interpolation: {
      // React already escapes rendered values
      escapeValue: false,
    },
    defaultNS: 'common',
  });

export function dateFnsLocale(): Locale {
  return i18n.language && i18n.language.startsWith('ru') ? ru : enUS;
}

export default i18n;
