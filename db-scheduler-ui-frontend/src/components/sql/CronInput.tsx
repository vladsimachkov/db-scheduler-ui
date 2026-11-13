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
import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  FormErrorMessage,
  FormHelperText,
  Input,
  Text,
} from '@chakra-ui/react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { previewSchedule } from 'src/services/sqlJobs';
import { cronDescription } from 'src/utils/cronDescription';
import { dateFormatText } from 'src/utils/dateFormatText';

interface CronInputProps {
  value: string;
  onChange: (value: string) => void;
  timezone?: string;
  isInvalid?: boolean;
  errorMessage?: string;
}

/**
 * Cron text field with a human-readable description (cronstrue) and a preview of the
 * next runs computed by the backend — i.e. by the very same CronSchedule the job will use.
 */
const CronInput: React.FC<CronInputProps> = ({
  value,
  onChange,
  timezone,
  isInvalid,
  errorMessage,
}) => {
  const { t } = useTranslation('sql');
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), 400);
    return () => clearTimeout(id);
  }, [value]);

  const trimmed = debounced.trim();

  const human = useMemo(() => cronDescription(trimmed), [trimmed]);

  const preview = useQuery({
    queryKey: ['cron-preview', trimmed, timezone ?? ''],
    queryFn: () => previewSchedule(trimmed, timezone || undefined, 3),
    enabled: trimmed.length > 0,
    staleTime: 60_000,
  });

  return (
    <Box>
      <Input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={'0 0/5 * * * ?'}
        fontFamily={'monospace'}
        isInvalid={isInvalid}
      />
      {isInvalid && errorMessage ? (
        <FormErrorMessage>{errorMessage}</FormErrorMessage>
      ) : (
        <FormHelperText>{t('cronHint')}</FormHelperText>
      )}
      {trimmed.length > 0 && (
        <Box mt={1}>
          {human && (
            <Text fontSize={'sm'} color="textSecondary">
              {human}
            </Text>
          )}
          {/* cronstrue may accept what the backend's CronSchedule rejects — trust the backend */}
          {(human === '' || preview.isError) && (
            <Text fontSize={'sm'} color="failed">
              {t('cronInvalid')}
            </Text>
          )}
          {preview.isSuccess && preview.data.length > 0 && (
            <Box fontSize={'sm'} color="textSecondary" mt={1}>
              <Text fontWeight={'medium'}>{t('nextRuns')}:</Text>
              {preview.data.map((time) => (
                <Text key={time}>{dateFormatText(new Date(time))}</Text>
              ))}
            </Box>
          )}
        </Box>
      )}
    </Box>
  );
};

export default CronInput;
