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
  Badge,
  Box,
  Button,
  FormControl,
  FormLabel,
  HStack,
  Input,
  Modal,
  ModalBody,
  ModalCloseButton,
  ModalContent,
  ModalFooter,
  ModalHeader,
  ModalOverlay,
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
import {
  AlertDialog,
  AlertDialogBody,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogOverlay,
} from '@chakra-ui/react';
import { User } from 'src/models/User';
import {
  USERS_QUERY_KEY,
  UserUpdateRequest,
  createUser,
  deleteUser,
  listUsers,
  resetPassword,
  updateUser,
} from 'src/services/users';
import { useAuth } from 'src/hooks/useAuth';
import { getReadonly } from 'src/utils/config';

const AdminUsersPage: React.FC = () => {
  const { t } = useTranslation('admin');
  const toast = useToast();
  const queryClient = useQueryClient();
  const readOnly = getReadonly();
  // the backend rejects self-demote/self-disable/self-delete; mirror that in the UI
  const { username: currentUsername } = useAuth();

  const [createOpen, setCreateOpen] = useState(false);
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [resetting, setResetting] = useState<User | undefined>(undefined);
  const [resetValue, setResetValue] = useState('');
  const [deleting, setDeleting] = useState<User | undefined>(undefined);
  const cancelRef = useRef(null);

  const { data: users } = useQuery({
    queryKey: [USERS_QUERY_KEY],
    queryFn: listUsers,
  });

  const invalidate = () =>
    queryClient.invalidateQueries({ queryKey: [USERS_QUERY_KEY] });
  const onError = (error: Error) =>
    toast({
      title: t('error', { message: error.message }),
      status: 'error',
      duration: 5000,
      position: 'top',
    });
  const okToast = (title: string) =>
    toast({ title, status: 'success', duration: 3000, position: 'top' });

  const create = useMutation({
    mutationFn: () => createUser({ username: newUsername.trim(), password: newPassword }),
    onSuccess: () => {
      invalidate();
      okToast(t('saved'));
      setCreateOpen(false);
      setNewUsername('');
      setNewPassword('');
    },
    onError,
  });

  const update = useMutation({
    mutationFn: ({ id, request }: { id: string; request: UserUpdateRequest }) =>
      updateUser(id, request),
    onSuccess: () => {
      invalidate();
      okToast(t('saved'));
    },
    onError,
  });

  const reset = useMutation({
    mutationFn: () => resetPassword(resetting?.id ?? '', resetValue),
    onSuccess: () => {
      okToast(t('saved'));
      setResetting(undefined);
      setResetValue('');
    },
    onError,
  });

  const remove = useMutation({
    mutationFn: (id: string) => deleteUser(id),
    onSuccess: () => {
      invalidate();
      okToast(t('deleted'));
    },
    onError,
    onSettled: () => setDeleting(undefined),
  });

  const badge = (on: boolean) => (
    <Badge colorScheme={on ? 'green' : 'gray'}>{on ? t('yes') : t('no')}</Badge>
  );

  return (
    <Box>
      <Box display={'flex'} alignItems={'center'} mb={4} gap={4}>
        <Text fontSize={'2xl'} fontWeight={'semibold'}>
          {t('usersTitle')}
        </Text>
        {!readOnly && (
          <Button ml={'auto'} colorScheme={'blue'} onClick={() => setCreateOpen(true)}>
            {t('addUser')}
          </Button>
        )}
      </Box>

      <TableContainer>
        <Table variant={'simple'}>
          <Thead>
            <Tr>
              <Th>{t('username')}</Th>
              <Th>{t('admin')}</Th>
              <Th>{t('enabled')}</Th>
              <Th isNumeric>{''}</Th>
            </Tr>
          </Thead>
          <Tbody>
            {users?.map((user) => {
              const self = user.username === currentUsername;
              return (
                <Tr key={user.id}>
                  <Td>{user.username}</Td>
                  <Td>{badge(user.admin)}</Td>
                  <Td>{badge(user.enabled)}</Td>
                  <Td isNumeric>
                    {!readOnly && (
                      <HStack spacing={1} justify={'flex-end'}>
                        <Button
                          size={'sm'}
                          variant={'ghost'}
                          isDisabled={self}
                          onClick={() =>
                            update.mutate({
                              id: user.id,
                              request: { admin: !user.admin },
                            })
                          }
                        >
                          {user.admin ? t('revokeAdmin') : t('makeAdmin')}
                        </Button>
                        <Button
                          size={'sm'}
                          variant={'ghost'}
                          isDisabled={self}
                          onClick={() =>
                            update.mutate({
                              id: user.id,
                              request: { enabled: !user.enabled },
                            })
                          }
                        >
                          {user.enabled ? t('disable') : t('enable')}
                        </Button>
                        <Button
                          size={'sm'}
                          variant={'ghost'}
                          onClick={() => {
                            setResetValue('');
                            setResetting(user);
                          }}
                        >
                          {t('resetPassword')}
                        </Button>
                        <Button
                          size={'sm'}
                          variant={'ghost'}
                          color="failed"
                          isDisabled={self}
                          onClick={() => setDeleting(user)}
                        >
                          {t('delete')}
                        </Button>
                      </HStack>
                    )}
                  </Td>
                </Tr>
              );
            })}
          </Tbody>
        </Table>
      </TableContainer>
      {users?.length === 0 && (
        <Text mt={2} color="textMuted">
          {t('empty')}
        </Text>
      )}

      <Modal
        isOpen={createOpen}
        onClose={() => {
          setCreateOpen(false);
          // never leave a half-typed password in a modal that will reopen
          setNewUsername('');
          setNewPassword('');
        }}
      >
        <ModalOverlay />
        <ModalContent>
          <form
            onSubmit={(event) => {
              event.preventDefault();
              create.mutate();
            }}
          >
            <ModalHeader>{t('newUserTitle')}</ModalHeader>
            <ModalCloseButton />
            <ModalBody>
              <FormControl isRequired>
                <FormLabel>{t('username')}</FormLabel>
                <Input
                  maxLength={100}
                  value={newUsername}
                  onChange={(event) => setNewUsername(event.target.value)}
                />
              </FormControl>
              <FormControl isRequired mt={4}>
                <FormLabel>{t('password')}</FormLabel>
                <Input
                  type={'password'}
                  maxLength={100}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                />
              </FormControl>
            </ModalBody>
            <ModalFooter>
              <Button
                type={'submit'}
                colorScheme={'blue'}
                isLoading={create.isLoading}
                isDisabled={!newUsername.trim() || !newPassword}
              >
                {t('save')}
              </Button>
            </ModalFooter>
          </form>
        </ModalContent>
      </Modal>

      <Modal isOpen={!!resetting} onClose={() => setResetting(undefined)}>
        <ModalOverlay />
        <ModalContent>
          <ModalHeader>
            {t('resetPasswordTitle', { username: resetting?.username ?? '' })}
          </ModalHeader>
          <ModalCloseButton />
          <ModalBody>
            <FormControl isRequired>
              <FormLabel>{t('password')}</FormLabel>
              <Input
                type={'password'}
                maxLength={100}
                value={resetValue}
                onChange={(event) => setResetValue(event.target.value)}
              />
            </FormControl>
          </ModalBody>
          <ModalFooter>
            <Button
              colorScheme={'blue'}
              isLoading={reset.isLoading}
              isDisabled={!resetValue}
              onClick={() => reset.mutate()}
            >
              {t('save')}
            </Button>
          </ModalFooter>
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
              {t('deleteUserTitle')}
            </AlertDialogHeader>
            <AlertDialogBody>
              {t('confirmDeleteUser', { username: deleting?.username ?? '' })}
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

export default AdminUsersPage;
