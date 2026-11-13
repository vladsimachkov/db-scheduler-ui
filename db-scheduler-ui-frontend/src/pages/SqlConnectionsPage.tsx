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
import React, { useState } from 'react';
import {
  AlertDialog,
  AlertDialogBody,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogOverlay,
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
  useToast,
} from '@chakra-ui/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import ConnectionForm from 'src/components/sql/ConnectionForm';
import { SqlConnection } from 'src/models/SqlConnection';
import {
  CONNECTIONS_QUERY_KEY,
  deleteConnection,
  listConnections,
} from 'src/services/sqlConnections';
import colors from 'src/styles/colors';
import { getReadonly } from 'src/utils/config';

const SqlConnectionsPage: React.FC = () => {
  const { t } = useTranslation('connections');
  const toast = useToast();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<SqlConnection | undefined>(undefined);
  const [deleting, setDeleting] = useState<SqlConnection | undefined>(undefined);
  const cancelRef = React.useRef(null);

  const { data: connections, isFetching } = useQuery({
    queryKey: [CONNECTIONS_QUERY_KEY],
    queryFn: listConnections,
  });

  const remove = useMutation({
    mutationFn: (id: string) => deleteConnection(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CONNECTIONS_QUERY_KEY] });
      toast({
        title: t('deleted'),
        status: 'success',
        duration: 3000,
        position: 'top',
      });
    },
    onError: (error: Error) =>
      toast({
        title: t('error', { message: error.message }),
        status: 'error',
        duration: 5000,
        position: 'top',
      }),
    onSettled: () => setDeleting(undefined),
  });

  const openCreate = () => {
    setEditing(undefined);
    setFormOpen(true);
  };
  const openEdit = (connection: SqlConnection) => {
    setEditing(connection);
    setFormOpen(true);
  };

  const readOnly = getReadonly();

  return (
    <Box>
      <Box display={'flex'} alignItems={'center'} mb={4} gap={4}>
        <Text fontSize={'2xl'} fontWeight={'semibold'}>
          {t('title')}
        </Text>
        {!readOnly && (
          <Button ml={'auto'} colorScheme={'blue'} onClick={openCreate}>
            {t('add')}
          </Button>
        )}
      </Box>

      <TableContainer>
        <Table variant={'simple'}>
          <Thead>
            <Tr>
              <Th>{t('table.name')}</Th>
              <Th>{t('table.dialect')}</Th>
              <Th>{t('table.url')}</Th>
              <Th>{t('table.user')}</Th>
              <Th>{t('table.password')}</Th>
              <Th isNumeric>{''}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {connections?.map((connection) => (
              <Tr key={connection.id}>
                <Td>{connection.name}</Td>
                <Td>{t(`dialectNames.${connection.dialect}`)}</Td>
                <Td
                  fontFamily={'monospace'}
                  fontSize={'xs'}
                  maxW={'400px'}
                  overflowWrap={'anywhere'}
                >
                  {connection.jdbcUrl}
                </Td>
                <Td>{connection.username ?? ''}</Td>
                <Td>
                  {connection.passwordSet
                    ? t('passwordYes')
                    : t('passwordNo')}
                </Td>
                <Td isNumeric>
                  {!readOnly && (
                    <Button
                      size={'sm'}
                      variant={'ghost'}
                      onClick={() => openEdit(connection)}
                    >
                      {t('edit')}
                    </Button>
                  )}
                  {!readOnly && (
                    <Button
                      size={'sm'}
                      variant={'ghost'}
                      color={colors.failed['200']}
                      onClick={() => setDeleting(connection)}
                    >
                      {t('delete')}
                    </Button>
                  )}
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      </TableContainer>
      {isFetching && (
        <Text mt={2} color={colors.primary['400']}>
          {t('common:loading')}
        </Text>
      )}
      {connections?.length === 0 && (
        <Text mt={2} color={colors.primary['400']}>
          {t('empty')}
        </Text>
      )}

      <ConnectionForm
        isOpen={formOpen}
        onClose={() => setFormOpen(false)}
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
                onClick={() => deleting && remove.mutate(deleting.id)}
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

export default SqlConnectionsPage;
