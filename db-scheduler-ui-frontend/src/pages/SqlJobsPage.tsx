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
import React, { useRef, useState } from 'react';
import {
  AlertDialog,
  AlertDialogBody,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogOverlay,
  Badge,
  Box,
  Button,
  HStack,
  IconButton,
  Select,
  Table,
  TableContainer,
  Tbody,
  Td,
  Text,
  Th,
  Thead,
  Tooltip,
  Tr,
  useToast,
} from '@chakra-ui/react';
import { DeleteIcon, EditIcon, InfoOutlineIcon } from '@chakra-ui/icons';
import { PlayIcon } from 'src/assets/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import SqlJobForm from 'src/components/sql/SqlJobForm';
import { SqlJob } from 'src/models/SqlJob';
import {
  CONNECTIONS_QUERY_KEY,
  listConnections,
} from 'src/services/sqlConnections';
import {
  JOBS_QUERY_KEY,
  deleteJob,
  listJobs,
  runJobNow,
} from 'src/services/sqlJobs';
import { useSchemas } from 'src/hooks/useSchemas';
import { cronDescription } from 'src/utils/cronDescription';
import { dateFormatText } from 'src/utils/dateFormatText';
import { getReadonly, getUsersEnabled } from 'src/utils/config';

const SqlJobsPage: React.FC = () => {
  const { t } = useTranslation('sql');
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<SqlJob | undefined>(undefined);
  const [deleting, setDeleting] = useState<SqlJob | undefined>(undefined);
  const [schemaFilter, setSchemaFilter] = useState('');
  const cancelRef = useRef(null);
  const usersEnabled = getUsersEnabled();

  const { data: jobs } = useQuery({
    queryKey: [JOBS_QUERY_KEY],
    queryFn: listJobs,
    refetchInterval: 2000,
  });
  const { data: connections } = useQuery({
    queryKey: [CONNECTIONS_QUERY_KEY],
    queryFn: listConnections,
  });
  const { data: schemas, isError: schemasError } = useSchemas();
  const visibleJobs = schemaFilter
    ? (jobs ?? []).filter((job) => job.schemaId === schemaFilter)
    : jobs;

  const invalidateJobs = () =>
    queryClient.invalidateQueries({ queryKey: [JOBS_QUERY_KEY] });

  const errorToast = (error: Error) =>
    toast({
      title: t('error', { message: error.message }),
      status: 'error',
      duration: 5000,
      position: 'top',
    });

  const runNow = useMutation({
    mutationFn: (jobId: string) => runJobNow(jobId),
    onSuccess: () => {
      invalidateJobs();
      toast({
        title: t('ranNow'),
        status: 'success',
        duration: 3000,
        position: 'top',
      });
    },
    onError: errorToast,
  });

  const remove = useMutation({
    mutationFn: (jobId: string) => deleteJob(jobId),
    onSuccess: () => {
      invalidateJobs();
      toast({
        title: t('deleted'),
        status: 'success',
        duration: 3000,
        position: 'top',
      });
    },
    onError: errorToast,
    onSettled: () => setDeleting(undefined),
  });

  const readOnly = getReadonly();

  const statusBadge = (job: SqlJob) => {
    if (job.running) {
      return (
        <Badge colorScheme={'blue'}>{t('status.running')}</Badge>
      );
    }
    if (job.consecutiveFailures > 0) {
      return (
        <Badge colorScheme={'red'}>
          {t('status.failing', { n: job.consecutiveFailures })}
        </Badge>
      );
    }
    if (!job.recurring) {
      return <Badge>{t('status.once')}</Badge>;
    }
    return <Badge colorScheme={'green'}>{t('status.ok')}</Badge>;
  };

  const lastRunCell = (job: SqlJob) => {
    const failureNewer =
      !!job.lastFailure &&
      (!job.lastSuccess || job.lastFailure > job.lastSuccess);
    const time = failureNewer ? job.lastFailure : job.lastSuccess;
    if (!time) {
      return <Text color="textMuted">—</Text>;
    }
    return (
      <Text color={failureNewer ? 'failed' : 'success'}>
        {dateFormatText(new Date(time))}
      </Text>
    );
  };

  return (
    <Box>
      <Box display={'flex'} alignItems={'center'} mb={4} gap={4}>
        <Text fontSize={'2xl'} fontWeight={'semibold'}>
          {t('title')}
        </Text>
        {usersEnabled && (
          <Select
            ml={'auto'}
            w={'auto'}
            maxW={'240px'}
            aria-label={t('schema')}
            value={schemaFilter}
            onChange={(event) => setSchemaFilter(event.target.value)}
          >
            <option value="">{t('allSchemas')}</option>
            {(schemas ?? []).map((schema) => (
              <option key={schema.id} value={schema.id}>
                {schema.name}
              </option>
            ))}
          </Select>
        )}
        {schemasError && (
          <Text color="failed" fontSize="sm">
            {t('schemasError')}
          </Text>
        )}
        {!readOnly && (
          <Button
            ml={usersEnabled ? 0 : 'auto'}
            colorScheme={'blue'}
            onClick={() => {
              setEditing(undefined);
              setFormOpen(true);
            }}
          >
            {t('create')}
          </Button>
        )}
      </Box>

      <TableContainer>
        <Table variant={'simple'}>
          <Thead>
            <Tr>
              <Th>{t('table.name')}</Th>
              <Th>{t('table.connection')}</Th>
              <Th>{t('table.schedule')}</Th>
              <Th>{t('table.nextRun')}</Th>
              <Th>{t('table.lastRun')}</Th>
              <Th>{t('table.status')}</Th>
              <Th isNumeric>{''}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {visibleJobs?.map((job) => (
              <Tr key={job.jobId}>
                <Td>
                  <Text fontWeight={'medium'}>{job.name}</Text>
                  {job.description && (
                    <Text fontSize={'xs'} color="textMuted">
                      {job.description}
                    </Text>
                  )}
                </Td>
                <Td>{job.connectionName ?? job.connectionId}</Td>
                <Td>
                  {job.recurring ? (
                    <>
                      <Text>
                        {cronDescription(job.cronExpression ?? '') ||
                          job.cronExpression}
                      </Text>
                      <Text
                        fontSize={'xs'}
                        fontFamily={'monospace'}
                        color="textMuted"
                      >
                        {job.cronExpression}
                      </Text>
                    </>
                  ) : (
                    t('scheduleOnce')
                  )}
                </Td>
                <Td>
                  {job.nextRunTime
                    ? dateFormatText(new Date(job.nextRunTime))
                    : '—'}
                </Td>
                <Td>{lastRunCell(job)}</Td>
                <Td>{statusBadge(job)}</Td>
                <Td isNumeric>
                  <HStack spacing={1} justify={'flex-end'}>
                    {!readOnly && (
                      <Tooltip label={t('runNow')}>
                        <IconButton
                          aria-label={t('runNow')}
                          icon={<PlayIcon boxSize={3} />}
                          size={'sm'}
                          variant={'ghost'}
                          isLoading={
                            runNow.isLoading && runNow.variables === job.jobId
                          }
                          onClick={() => runNow.mutate(job.jobId)}
                        />
                      </Tooltip>
                    )}
                    <Tooltip label={t('history')}>
                      <IconButton
                        aria-label={t('history')}
                        icon={<InfoOutlineIcon boxSize={3} />}
                        size={'sm'}
                        variant={'ghost'}
                        onClick={() =>
                          navigate(`/sql-jobs/${job.jobId}/executions`)
                        }
                      />
                    </Tooltip>
                    {!readOnly && (
                      <Tooltip label={t('edit')}>
                        <IconButton
                          aria-label={t('edit')}
                          icon={<EditIcon boxSize={3} />}
                          size={'sm'}
                          variant={'ghost'}
                          onClick={() => {
                            setEditing(job);
                            setFormOpen(true);
                          }}
                        />
                      </Tooltip>
                    )}
                    {!readOnly && (
                      <Tooltip label={t('delete')}>
                        <IconButton
                          aria-label={t('delete')}
                          icon={<DeleteIcon boxSize={3} />}
                          size={'sm'}
                          variant={'ghost'}
                          color="failed"
                          onClick={() => setDeleting(job)}
                        />
                      </Tooltip>
                    )}
                  </HStack>
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      </TableContainer>
      {jobs && visibleJobs?.length === 0 && (
        <Text mt={2} color="textMuted">
          {schemaFilter ? t('noMatchingJobs') : t('empty')}
        </Text>
      )}

      <SqlJobForm
        isOpen={formOpen}
        onClose={() => setFormOpen(false)}
        connections={connections ?? []}
        schemas={schemas ?? []}
        initial={editing}
      />

      <AlertDialog
        isOpen={!!deleting}
        leastDestructiveRef={cancelRef}
        onClose={() => setDeleting(undefined)}
      >
        <AlertDialogOverlay>
          <AlertDialogContent>
            <AlertDialogHeader fontSize="lg" fontWeight="bold">
              {t('confirmDeleteTitle')}
            </AlertDialogHeader>
            <AlertDialogBody>
              {t('confirmDeleteBody', { name: deleting?.name ?? '' })}
            </AlertDialogBody>
            <AlertDialogFooter>
              <Button ref={cancelRef} onClick={() => setDeleting(undefined)}>
                {t('common:cancel')}
              </Button>
              <Button
                colorScheme="red"
                isLoading={remove.isLoading}
                onClick={() => deleting && remove.mutate(deleting.jobId)}
                ml={3}
              >
                {t('delete')}
              </Button>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialogOverlay>
      </AlertDialog>
    </Box>
  );
};

export default SqlJobsPage;
