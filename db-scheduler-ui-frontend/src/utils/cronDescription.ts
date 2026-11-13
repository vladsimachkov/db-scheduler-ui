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
import cronstrue from 'cronstrue';
import 'cronstrue/locales/ru';
import i18n from 'src/i18n';

/**
 * Human-readable description of a 6-field cron expression in the active language.
 * Returns '' for expressions cronstrue cannot parse — callers decide how to signal invalidity.
 */
export function cronDescription(cron: string): string {
  try {
    return cronstrue.toString(cron, {
      locale: i18n.language?.startsWith('ru') ? 'ru' : 'en',
    });
  } catch {
    return '';
  }
}
