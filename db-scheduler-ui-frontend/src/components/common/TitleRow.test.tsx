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
import '@testing-library/jest-dom/vitest';
import { ChakraProvider } from '@chakra-ui/react';
import { render, screen } from '@testing-library/react';
import React from 'react';
import { beforeAll, describe, expect, it, vi } from 'vitest';
import TitleRow from 'src/components/common/TitleRow';
import i18n from 'src/i18n';
import { SortBy } from 'src/models/QueryParams';

const renderRow = (showSchema?: boolean) =>
  render(
    <ChakraProvider>
      <TitleRow
        currentSort={SortBy.Default}
        setCurrentSort={vi.fn()}
        sortAsc={true}
        setSortAsc={vi.fn()}
        showSchema={showSchema}
      />
    </ChakraProvider>,
  );

beforeAll(async () => {
  await i18n.changeLanguage('en');
});

describe('TitleRow schema header', () => {
  it('shows the Schema header when the column is on', () => {
    renderRow(true);

    expect(screen.getByText('Schema')).toBeInTheDocument();
  });

  it('omits the Schema header when the column is off', () => {
    renderRow(false);

    expect(screen.queryByText('Schema')).not.toBeInTheDocument();
    expect(screen.getByText('Task-ID')).toBeInTheDocument();
  });
});
