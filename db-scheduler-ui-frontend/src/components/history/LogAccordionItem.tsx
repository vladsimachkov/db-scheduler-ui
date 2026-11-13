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
import { AccordionPanel, Box, Divider, Text, VStack } from '@chakra-ui/react';
import { useTranslation } from 'react-i18next';
import { LogDataRow } from 'src/components/history/LogDataRow';
interface LogAccordionItemProps {
  taskData: object | null;
  stackTrace: string | null;
}

export const LogAccordionItem: React.FC<LogAccordionItemProps> = ({
  stackTrace,
  taskData,
}) => {
  const { t } = useTranslation();
  return (
    <AccordionPanel overflowX="auto" p={0}>
      <Box display={'flex'} justifyContent={'space-between'}></Box>
      <VStack
        align="start"
        spacing={2}
        bgColor={'surface'}
        p={0}
        w={'100%'}
        borderRadius={4}
      >
        {stackTrace && (
          <Text
            ml={'16px'}
            textDecoration={'underline'}
            color={'textSecondary'}
          >
            {t('history:details.stacktrace')}
          </Text>
        )}

        <pre style={{ marginLeft: '16px', marginTop: '8px' }}>{stackTrace}</pre>
        {stackTrace && taskData && <Divider color={'border'} />}
        {taskData && (
          <Text
            ml={'16px'}
            textDecoration={'underline'}
            color={'textSecondary'}
          >
            {t('history:details.taskData')}
          </Text>
        )}

        <LogDataRow taskData={taskData} />
      </VStack>
    </AccordionPanel>
  );
};
