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
import React from 'react';
import {
  Badge,
  Box,
  Button,
  Table,
  TableContainer,
  Tbody,
  Td,
  Text,
  Th,
  Thead,
  Tr,
} from '@chakra-ui/react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { getJob, listExecutions } from 'src/services/sqlJobs';
import colors from 'src/styles/colors';
import { dateFormatText } from 'src/utils/dateFormatText';

function formatDuration(ms: number): string {
  if (ms < 1000) {
    return `${ms} ms`;
  }
  const seconds = ms / 1000;
  if (seconds < 60) {
    return `${seconds.toFixed(1)} s`;
  }
  return `${Math.floor(seconds / 60)} min ${Math.round(seconds % 60)} s`;
}

const SqlJobExecutionsPage: React.FC = () => {
  const { t } = useTranslation('sql');
  const navigate = useNavigate();
  const { jobId } = useParams<{ jobId: string }>();

  const { data: job } = useQuery({
    queryKey: ['sql-job', jobId],
    queryFn: () => getJob(jobId ?? ''),
    enabled: !!jobId,
  });

  const { data: executions } = useQuery({
    queryKey: ['sql-executions', jobId],
    queryFn: () => listExecutions(jobId ?? ''),
    enabled: !!jobId,
    refetchInterval: 2000,
  });

  return (
    <Box>
      <Box display={'flex'} alignItems={'center'} mb={4} gap={4}>
        <Button variant={'ghost'} onClick={() => navigate('/sql-jobs')}>
          ← {t('executions.back')}
        </Button>
        <Text fontSize={'2xl'} fontWeight={'semibold'}>
          {t('executions.title', { name: job?.name ?? jobId })}
        </Text>
      </Box>

      <TableContainer>
        <Table variant={'simple'} size={'sm'}>
          <Thead>
            <Tr>
              <Th>{t('executions.started')}</Th>
              <Th>{t('executions.duration')}</Th>
              <Th>{t('executions.statements')}</Th>
              <Th>{t('executions.rows')}</Th>
              <Th>{t('executions.result')}</Th>
              <Th>{t('executions.trigger')}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {executions?.map((execution) => (
              <Tr key={execution.id}>
                <Td>{dateFormatText(new Date(execution.timeStarted))}</Td>
                <Td>{formatDuration(execution.durationMs)}</Td>
                <Td>{execution.statementsExecuted}</Td>
                <Td>
                  {execution.rowsAffected}
                  {execution.rowsCapped && (
                    <Badge ml={1} colorScheme={'yellow'}>
                      {t('executions.rowsCapped')}
                    </Badge>
                  )}
                </Td>
                <Td>
                  {execution.succeeded ? (
                    <Badge colorScheme={'green'}>
                      {t('executions.success')}
                    </Badge>
                  ) : (
                    <Box>
                      <Badge colorScheme={'red'}>
                        {t('executions.failure')}
                      </Badge>
                      {execution.errorMessage && (
                        <Text
                          fontSize={'xs'}
                          color={colors.failed['200']}
                          maxW={'400px'}
                          overflowWrap={'anywhere'}
                        >
                          {execution.errorMessage}
                        </Text>
                      )}
                    </Box>
                  )}
                </Td>
                <Td>
                  {t(`trigger.${execution.triggeredBy}`, execution.triggeredBy)}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      </TableContainer>
      {executions?.length === 0 && (
        <Text mt={2} color={colors.primary['400']}>
          {t('executions.empty')}
        </Text>
      )}
    </Box>
  );
};

export default SqlJobExecutionsPage;
