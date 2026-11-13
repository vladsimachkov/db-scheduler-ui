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
import DatePickerImport from 'react-datepicker';
import { Box } from '@chakra-ui/react';
import 'react-datepicker/dist/react-datepicker.css';
import { useTranslation } from 'react-i18next';
import { interopDefault } from 'src/utils/interopDefault';
import { dateFnsLocale } from 'src/i18n';

// react-datepicker v4 is CommonJS; under Vite 8's interop the default import resolves to the
// module namespace rather than the component. See interopDefault.
const DatePicker = interopDefault(DatePickerImport);

interface DateTimeInputProps {
  selectedDate: Date | null;
  onChange: (date: Date | null) => void;
  forceFutureTime?: boolean;
}

export const DateTimeInput: React.FC<DateTimeInputProps> = ({
  selectedDate,
  onChange,
  forceFutureTime,
}) => {
  const { t } = useTranslation();
  return (
    <Box
      p={1}
      borderRadius={4}
      borderColor={'border'}
      borderWidth={1}
      backgroundColor={'surface'}
      w="fit-content"
    >
      <DatePicker
        selected={selectedDate}
        onChange={onChange}
        showTimeSelect
        locale={dateFnsLocale()}
        timeFormat={'HH:mm'}
        dateFormat={t('common:dateTime.format')}
        placeholderText={t('common:dateTime.placeholder')}
        useWeekdaysShort
        minTime={
          selectedDate?.toDateString() === new Date().toDateString() &&
          forceFutureTime
            ? new Date()
            : new Date(0, 0, 1)
        }
        maxTime={new Date(2100, 0, 1, 23, 59, 59, 999)}
        minDate={forceFutureTime ? new Date() : new Date(0)}
        maxDate={new Date(2100, 0, 1)}
      ></DatePicker>
    </Box>
  );
};
