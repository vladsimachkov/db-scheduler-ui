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
  Box,
  Button,
  FormControl,
  FormLabel,
  HStack,
  Input,
  List,
  ListItem,
  Modal,
  ModalBody,
  ModalCloseButton,
  ModalContent,
  ModalFooter,
  ModalHeader,
  ModalOverlay,
  Select,
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
import {
  AlertDialog,
  AlertDialogBody,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogOverlay,
} from '@chakra-ui/react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Schema } from 'src/models/Schema';
import {
  GRANTS_QUERY_KEY,
  SCHEMAS_QUERY_KEY,
  createSchema,
  deleteSchema,
  grantAccess,
  listGrants,
  revokeAccess,
  updateSchema,
} from 'src/services/schemas';
import { useSchemas } from 'src/hooks/useSchemas';
import { USERS_QUERY_KEY, listUsers } from 'src/services/users';
import { getReadonly } from 'src/utils/config';

const AdminSchemasPage: React.FC = () => {
  const { t } = useTranslation('admin');
  const toast = useToast();
  const queryClient = useQueryClient();
  const readOnly = getReadonly();

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<Schema | undefined>(undefined);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [deleting, setDeleting] = useState<Schema | undefined>(undefined);
  const [grantsFor, setGrantsFor] = useState<Schema | undefined>(undefined);
  const [grantCandidate, setGrantCandidate] = useState('');
  const cancelRef = useRef(null);

  const { data: schemas } = useSchemas();
  // only fetched while the grant panel is open
  const { data: users } = useQuery({
    queryKey: [USERS_QUERY_KEY],
    queryFn: listUsers,
    enabled: !!grantsFor,
  });
  const { data: grants } = useQuery({
    queryKey: [GRANTS_QUERY_KEY, grantsFor?.id],
    queryFn: () => listGrants(grantsFor?.id ?? ''),
    enabled: !!grantsFor,
  });

  const invalidateSchemas = () =>
    queryClient.invalidateQueries({ queryKey: [SCHEMAS_QUERY_KEY] });
  const invalidateGrants = () =>
    queryClient.invalidateQueries({ queryKey: [GRANTS_QUERY_KEY] });
  const onError = (error: Error) =>
    toast({
      title: t('error', { message: error.message }),
      status: 'error',
      duration: 5000,
      position: 'top',
    });
  const okToast = (title: string) =>
    toast({ title, status: 'success', duration: 3000, position: 'top' });

  const save = useMutation({
    mutationFn: () =>
      editing
        ? updateSchema(editing.id, {
            name: name.trim(),
            description: description.trim() || undefined,
          })
        : createSchema({
            name: name.trim(),
            description: description.trim() || undefined,
          }),
    onSuccess: () => {
      invalidateSchemas();
      okToast(t('saved'));
      setFormOpen(false);
    },
    onError,
  });

  const remove = useMutation({
    mutationFn: (id: string) => deleteSchema(id),
    onSuccess: () => {
      invalidateSchemas();
      okToast(t('deleted'));
    },
    onError,
    onSettled: () => setDeleting(undefined),
  });

  const grant = useMutation({
    mutationFn: (username: string) => grantAccess(grantsFor?.id ?? '', username),
    onSuccess: () => {
      invalidateGrants();
      setGrantCandidate('');
    },
    onError,
  });

  const revoke = useMutation({
    mutationFn: (username: string) => revokeAccess(grantsFor?.id ?? '', username),
    onSuccess: invalidateGrants,
    onError,
  });

  const openCreate = () => {
    setEditing(undefined);
    setName('');
    setDescription('');
    setFormOpen(true);
  };
  const openEdit = (schema: Schema) => {
    setEditing(schema);
    setName(schema.name);
    setDescription(schema.description ?? '');
    setFormOpen(true);
  };

  const granted = grants ?? [];
  const candidates = (users ?? []).filter(
    (user) => !granted.includes(user.username),
  );

  return (
    <Box>
      <Box display={'flex'} alignItems={'center'} mb={4} gap={4}>
        <Text fontSize={'2xl'} fontWeight={'semibold'}>
          {t('schemasTitle')}
        </Text>
        {!readOnly && (
          <Button ml={'auto'} colorScheme={'blue'} onClick={openCreate}>
            {t('addSchema')}
          </Button>
        )}
      </Box>

      <TableContainer>
        <Table variant={'simple'}>
          <Thead>
            <Tr>
              <Th>{t('name')}</Th>
              <Th>{t('description')}</Th>
              <Th isNumeric>{''}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {schemas?.map((schema) => (
              <Tr key={schema.id}>
                <Td>{schema.name}</Td>
                <Td color="textMuted">{schema.description ?? ''}</Td>
                <Td isNumeric>
                  <HStack spacing={1} justify={'flex-end'}>
                    <Button
                      size={'sm'}
                      variant={'ghost'}
                      onClick={() => setGrantsFor(schema)}
                    >
                      {t('grants')}
                    </Button>
                    {!readOnly && (
                      <Button size={'sm'} variant={'ghost'} onClick={() => openEdit(schema)}>
                        {t('edit')}
                      </Button>
                    )}
                    {!readOnly && (
                      <Button
                        size={'sm'}
                        variant={'ghost'}
                        color="failed"
                        onClick={() => setDeleting(schema)}
                      >
                        {t('delete')}
                      </Button>
                    )}
                  </HStack>
                </Td>
              </Tr>
            ))}
          </Tbody>
        </Table>
      </TableContainer>
      {schemas?.length === 0 && (
        <Text mt={2} color="textMuted">
          {t('empty')}
        </Text>
      )}

      <Modal isOpen={formOpen} onClose={() => setFormOpen(false)}>
        <ModalOverlay />
        <ModalContent>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              save.mutate();
            }}
          >
            <ModalHeader>
              {editing ? t('editSchemaTitle') : t('newSchemaTitle')}
            </ModalHeader>
            <ModalCloseButton />
            <ModalBody>
              <FormControl isRequired>
                <FormLabel>{t('name')}</FormLabel>
                <Input value={name} onChange={(event) => setName(event.target.value)} />
              </FormControl>
              <FormControl mt={4}>
                <FormLabel>{t('description')}</FormLabel>
                <Input
                  value={description}
                  onChange={(event) => setDescription(event.target.value)}
                />
              </FormControl>
            </ModalBody>
            <ModalFooter>
              <Button
                type={'submit'}
                colorScheme={'blue'}
                isLoading={save.isLoading}
                isDisabled={!name.trim()}
              >
                {t('save')}
              </Button>
            </ModalFooter>
          </form>
        </ModalContent>
      </Modal>

      <Modal
        isOpen={!!grantsFor}
        onClose={() => {
          setGrantsFor(undefined);
          setGrantCandidate('');
        }}
        size={'md'}
      >
        <ModalOverlay />
        <ModalContent>
          <ModalHeader>
            {t('grantsTitle', { name: grantsFor?.name ?? '' })}
          </ModalHeader>
          <ModalCloseButton />
          <ModalBody>
            <Text fontWeight={'semibold'} mb={2}>
              {t('grantedUsers')}
            </Text>
            {granted.length === 0 ? (
              <Text color="textMuted">{t('noGrants')}</Text>
            ) : (
              <List spacing={1}>
                {granted.map((username) => (
                  <ListItem key={username} display={'flex'} alignItems={'center'} gap={2}>
                    <Box flex={1}>{username}</Box>
                    {!readOnly && (
                      <Button
                        size={'sm'}
                        variant={'ghost'}
                        onClick={() => revoke.mutate(username)}
                      >
                        {t('revoke')}
                      </Button>
                    )}
                  </ListItem>
                ))}
              </List>
            )}
            {!readOnly && candidates.length > 0 && (
              <HStack mt={4}>
                <Select
                  flex={1}
                  aria-label={t('grantUserPlaceholder')}
                  placeholder={t('grantUserPlaceholder')}
                  value={grantCandidate}
                  onChange={(event) => setGrantCandidate(event.target.value)}
                >
                  {candidates.map((user) => (
                    <option key={user.id} value={user.username}>
                      {user.username}
                    </option>
                  ))}
                </Select>
                <Button
                  colorScheme={'blue'}
                  isDisabled={!grantCandidate}
                  isLoading={grant.isLoading}
                  onClick={() => grantCandidate && grant.mutate(grantCandidate)}
                >
                  {t('grant')}
                </Button>
              </HStack>
            )}
          </ModalBody>
        </ModalContent>
      </Modal>

      <AlertDialog
        isOpen={!!deleting}
        leastDestructiveRef={cancelRef}
        onClose={() => setDeleting(undefined)}
      >
        <AlertDialogOverlay>
          <AlertDialogContent>
            <AlertDialogHeader fontSize="lg" fontWeight="bold">
              {t('deleteSchemaTitle')}
            </AlertDialogHeader>
            <AlertDialogBody>
              {t('confirmDeleteSchema', { name: deleting?.name ?? '' })}
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

export default AdminSchemasPage;
