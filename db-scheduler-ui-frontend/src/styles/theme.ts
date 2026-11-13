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
import { extendTheme } from '@chakra-ui/react';
import { datepickerDark } from 'src/styles/datepickerDark';

// Semantic color tokens consumed as plain strings (bg="surface", color="textMuted", ...).
// `default` is the light-mode value, `_dark` applies when the color mode is dark
// (body.chakra-ui-dark or html[data-theme=dark]). Light values match the legacy
// src/styles/colors.ts palette verbatim, so light mode renders unchanged.
export const theme = extendTheme({
  config: {
    // stock Chakra color mode: follow the OS, a manual toggle sticks until
    // the next OS-level theme change (useSystemColorMode keeps them in sync)
    initialColorMode: 'system',
    useSystemColorMode: true,
  },
  semanticTokens: {
    colors: {
      // neutral surfaces and text
      surface: { default: '#ffffff', _dark: '#262626' },
      textOnSolid: { default: '#ffffff', _dark: '#ffffff' },
      pageBg: { default: '#efefef', _dark: '#1a1a1a' },
      surfaceSubtle: { default: '#efefef', _dark: '#333333' },
      border: { default: '#d2d2d2', _dark: '#4a4a4a' },
      textFaint: { default: '#d2d2d2', _dark: '#6b6b6b' },
      textMuted: { default: '#787878', _dark: '#9e9e9e' },
      textSecondary: { default: '#4f4f4f', _dark: '#c9c9c9' },
      textHeading: { default: '#3b3b3b', _dark: '#ececec' },
      textStrong: { default: '#1a1a1a', _dark: '#f5f5f5' },
      brand: { default: '#002FA7', _dark: '#8AA6FF' },
      // checked form-control fill (kept dark in both modes for white check icon)
      controlChecked: { default: '#4f4f4f', _dark: '#6b6b6b' },
      // status scales
      runningFill: { default: '#DAE2F6', _dark: '#1e2a52' },
      runningFillHover: { default: '#cfd5ff', _dark: '#2a3a6e' },
      running: { default: '#5068F6', _dark: '#7d92ff' },
      // solid fills that keep white text legible in both modes
      runningSolid: { default: '#5068F6', _dark: '#3d55d8' },
      runningSolidHover: { default: '#455ad2', _dark: '#2f43ad' },
      runningText: { default: '#3948a6', _dark: '#b9c6ff' },
      failedFill: { default: '#EFC2C2', _dark: '#4a1d1d' },
      failed: { default: '#BB0101', _dark: '#ff6b6b' },
      successFill: { default: '#68D391', _dark: '#1e4630' },
      success: { default: '#08662C', _dark: '#68D391' },
      // overview page specifics
      overdue: { default: '#725200', _dark: '#f0c04a' },
      dotDormant: { default: '#cfd6dc', _dark: '#5a6570' },
      rowFailedBg: { default: '#fcf3f3', _dark: '#2a1717' },
      rowRunningBg: { default: '#f6f8fd', _dark: '#1a2135' },
      rowDormantBg: { default: '#f8f9fa', _dark: '#1e1e1e' },
      rowFailedHover: { default: '#f7e8e8', _dark: '#331c1c' },
      rowRunningHover: { default: '#eef2fc', _dark: '#212a44' },
      rowDormantHover: { default: '#f1f2f3', _dark: '#262626' },
      rowFailedBorder: { default: '#f3dede', _dark: '#4a2a2a' },
      rowRunningBorder: { default: '#e7edf9', _dark: '#2e3a5e' },
      rowDormantBorder: { default: '#eceef0', _dark: '#3a3a3a' },
    },
  },
  styles: {
    global: {
      body: {
        bg: 'pageBg',
      },
      ...datepickerDark,
    },
  },
});
