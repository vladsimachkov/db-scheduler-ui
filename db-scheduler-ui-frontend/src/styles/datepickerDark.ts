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

// react-datepicker ships a light-only stylesheet with hardcoded colors. These
// global overrides apply in dark mode only (the popup renders in a portal under
// <html>, so the data-theme attribute is the reliable hook) and reuse the Chakra
// semantic-token CSS variables so the popup stays in sync with the theme.
const dark = 'html[data-theme=dark] ';

export const datepickerDark = {
  [`${dark}.react-datepicker`]: {
    backgroundColor: 'var(--chakra-colors-surface)',
    borderColor: 'var(--chakra-colors-border)',
    color: 'var(--chakra-colors-textStrong)',
  },
  [`${dark}.react-datepicker__header`]: {
    backgroundColor: 'var(--chakra-colors-surfaceSubtle)',
    borderBottomColor: 'var(--chakra-colors-border)',
  },
  [`${dark}.react-datepicker__current-month, ${dark}.react-datepicker-time__header, ${dark}.react-datepicker-year-header`]:
    {
      color: 'var(--chakra-colors-textStrong)',
    },
  [`${dark}.react-datepicker__day-name, ${dark}.react-datepicker__day, ${dark}.react-datepicker__time-name`]:
    {
      color: 'var(--chakra-colors-textStrong)',
    },
  [`${dark}.react-datepicker__navigation-icon::before`]: {
    borderColor: 'var(--chakra-colors-textMuted)',
  },
  [`${dark}.react-datepicker__day:hover, ${dark}.react-datepicker__month-text:hover, ${dark}.react-datepicker__quarter-text:hover, ${dark}.react-datepicker__year-text:hover`]:
    {
      backgroundColor: 'var(--chakra-colors-surfaceSubtle)',
    },
  [`${dark}.react-datepicker__day--selected, ${dark}.react-datepicker__day--keyboard-selected, ${dark}.react-datepicker__month-text--selected, ${dark}.react-datepicker__time-list-item--selected`]:
    {
      backgroundColor: 'var(--chakra-colors-runningSolid)',
      color: 'var(--chakra-colors-textOnSolid)',
    },
  [`${dark}.react-datepicker__day--disabled, ${dark}.react-datepicker__day--outside-month`]:
    {
      color: 'var(--chakra-colors-textFaint)',
    },
  [`${dark}.react-datepicker__time-container, ${dark}.react-datepicker__time, ${dark}.react-datepicker__time-box, ${dark}.react-datepicker__time-list-item`]:
    {
      backgroundColor: 'var(--chakra-colors-surface)',
      borderColor: 'var(--chakra-colors-border)',
      color: 'var(--chakra-colors-textStrong)',
    },
  [`${dark}.react-datepicker__time-list-item:hover`]: {
    backgroundColor: 'var(--chakra-colors-surfaceSubtle)',
  },
};
