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
import { Box } from '@chakra-ui/react';
import { useTranslation } from 'react-i18next';
import { NumberCircle } from 'src/components/common/NumberCircle';

interface StatusBoxProps {
  status: string;
  consecutiveFailures: number;
}

const statusColors: Record<
  string,
  { borderColor: string; backgroundColor: string; color: string }
> = {
  Failed: {
    borderColor: 'failed',
    backgroundColor: 'failedFill',
    color: 'failed',
  },
  Running: {
    borderColor: 'runningFill',
    backgroundColor: 'runningFill',
    color: 'textStrong',
  },
  Scheduled: {
    borderColor: 'border',
    backgroundColor: 'surfaceSubtle',
    color: 'textStrong',
  },
  Group: {
    borderColor: 'textStrong',
    backgroundColor: 'surfaceSubtle',
    color: 'textStrong',
  },
};

export const StatusBox: React.FC<StatusBoxProps> = ({
  status,
  consecutiveFailures,
}) => {
  const { t } = useTranslation();
  const statusInfo =
    status === 'Group'
      ? statusColors['Group']
      : statusColors[status] || statusColors['Scheduled'];
  const { borderColor, backgroundColor, color } = statusInfo;

  return (
    <Box
      textAlign={'center'}
      borderRadius={4}
      mr={4}
      width={115}
      borderColor={borderColor}
      backgroundColor={backgroundColor}
      color={color}
      px={4}
      py={1}
      borderWidth={1}
      position="relative"
      borderStyle={status !== 'Group' ? 'solid' : 'dashed'}
    >
      {consecutiveFailures > 0 && status !== 'Group' ? (
        <NumberCircle
          number={consecutiveFailures}
          bgColor={'failed'}
          transform={'translate(50%, -50%)'}
        ></NumberCircle>
      ) : (
        <></>
      )}

      {t(`scheduled:status.${status.toLowerCase()}`)}
    </Box>
  );
};
