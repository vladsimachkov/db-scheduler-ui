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
import React, { useEffect, useState } from 'react';
import {
  Button,
  Checkbox,
  FormControl,
  FormErrorMessage,
  FormHelperText,
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
  Select,
  Switch,
  Text,
  useToast,
  VStack,
} from '@chakra-ui/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import {
  buildJdbcUrl,
  connectionFormSchema,
  ConnectionFormValues,
  DEFAULT_PORTS,
  formErrorText,
} from 'src/components/sql/formSchemas';
import {
  SqlConnection,
  SqlConnectionRequest,
  SqlDialect,
} from 'src/models/SqlConnection';
import {
  CONNECTIONS_QUERY_KEY,
  createConnection,
  testConnection,
  updateConnection,
} from 'src/services/sqlConnections';
import colors from 'src/styles/colors';

const DIALECTS: SqlDialect[] = [
  'ORACLE',
  'TRINO',
  'IMPALA',
  'MYSQL',
  'GENERIC',
];

function defaultValuesFor(initial?: SqlConnection): ConnectionFormValues {
  return {
    name: initial?.name ?? '',
    dialect: initial?.dialect ?? 'ORACLE',
    // Editing an existing connection starts from its stored URL; guided mode is for new ones.
    guided: !initial,
    host: '',
    port: DEFAULT_PORTS[initial?.dialect ?? 'ORACLE'],
    database: '',
    jdbcUrl: initial?.jdbcUrl ?? '',
    driverClass: initial?.driverClass ?? '',
    username: initial?.username ?? '',
    password: '',
    clearPassword: false,
    propertiesJson: initial?.propertiesJson ?? '',
  };
}

interface ConnectionFormProps {
  isOpen: boolean;
  onClose: () => void;
  initial?: SqlConnection;
}

const ConnectionForm: React.FC<ConnectionFormProps> = ({
  isOpen,
  onClose,
  initial,
}) => {
  const { t } = useTranslation('connections');
  const toast = useToast();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<string | null>(null);
  const [testFailed, setTestFailed] = useState(false);

  const {
    register,
    handleSubmit,
    control,
    watch,
    setValue,
    reset,
    formState: { errors },
  } = useForm<ConnectionFormValues>({
    resolver: zodResolver(connectionFormSchema),
    defaultValues: defaultValuesFor(initial),
  });

  useEffect(() => {
    if (isOpen) {
      reset(defaultValuesFor(initial));
      setFormError(null);
      setTestResult(null);
      setTestFailed(false);
    }
    // Keyed on the stable id, not the object: a refetch would otherwise wipe
    // in-progress edits with a fresh-but-identical SqlConnection instance.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, initial?.id]);

  const values = watch();
  const guided = values.guided && values.dialect !== 'GENERIC';
  const clearPassword = values.clearPassword;
  const urlPreview = guided ? buildJdbcUrl(values) : values.jdbcUrl.trim();

  const save = useMutation({
    mutationFn: (payload: SqlConnectionRequest) =>
      initial
        ? updateConnection(initial.id, payload)
        : createConnection(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [CONNECTIONS_QUERY_KEY] });
      toast({
        title: t('saved'),
        status: 'success',
        duration: 3000,
        position: 'top',
      });
      onClose();
    },
    onError: (error: Error) => setFormError(error.message),
  });

  const test = useMutation({
    mutationFn: () => testConnection(initial?.id ?? ''),
    onSuccess: (result) => {
      setTestFailed(!result.success);
      setTestResult(
        result.success
          ? t('testOk', {
              product: result.productName ?? '',
              version: result.productVersion ?? '',
            })
          : t('testFail', { error: result.error ?? '' }),
      );
    },
    onError: (error: Error) => {
      setTestFailed(true);
      setTestResult(t('testFail', { error: error.message }));
    },
  });

  const onSubmit = handleSubmit((formValues) => {
    setFormError(null);
    save.mutate({
      name: formValues.name.trim(),
      dialect: formValues.dialect,
      jdbcUrl: guided
        ? buildJdbcUrl(formValues)
        : formValues.jdbcUrl.trim(),
      driverClass: formValues.driverClass.trim() || undefined,
      username: formValues.username.trim() || undefined,
      // clearPassword wins on the backend, so never send a password alongside it
      password: formValues.clearPassword ? undefined : formValues.password || undefined,
      clearPassword: formValues.clearPassword || undefined,
      propertiesJson: formValues.propertiesJson.trim() || undefined,
    });
  });

  return (
    <Modal isOpen={isOpen} onClose={onClose} size={'xl'}>
      <ModalOverlay />
      <ModalContent>
        <form onSubmit={onSubmit}>
          <ModalHeader>{initial ? t('edit') : t('add')}</ModalHeader>
          <ModalCloseButton />
          <ModalBody>
            <VStack spacing={4} align={'stretch'}>
              <FormControl isInvalid={!!errors.name}>
                <FormLabel>{t('name')}</FormLabel>
                <Input {...register('name')} />
                {errors.name && (
                  <FormErrorMessage>
                    {formErrorText(t, errors.name.message, 'required')}
                  </FormErrorMessage>
                )}
              </FormControl>

              <HStack spacing={4} align={'start'}>
                <FormControl flex={1}>
                  <FormLabel>{t('dialect')}</FormLabel>
                  <Controller
                    control={control}
                    name="dialect"
                    render={({ field }) => (
                      <Select
                        value={field.value}
                        onChange={(event) => {
                          const dialect = event.target.value as SqlDialect;
                          field.onChange(dialect);
                          if (dialect === 'GENERIC') {
                            setValue('guided', false);
                          } else {
                            setValue('port', DEFAULT_PORTS[dialect]);
                          }
                        }}
                      >
                        {DIALECTS.map((dialect) => (
                          <option key={dialect} value={dialect}>
                            {t(`dialectNames.${dialect}`)}
                          </option>
                        ))}
                      </Select>
                    )}
                  />
                </FormControl>
                <FormControl flex={1}>
                  <FormLabel>{t('username')}</FormLabel>
                  <Input {...register('username')} autoComplete={'off'} />
                </FormControl>
              </HStack>

              {values.dialect !== 'GENERIC' && (
                <HStack>
                  <Switch
                    id="guided-mode"
                    isChecked={guided}
                    onChange={(event) =>
                      setValue('guided', event.target.checked)
                    }
                  />
                  <FormLabel htmlFor="guided-mode" m={0}>
                    {guided ? t('guided') : t('rawUrl')}
                  </FormLabel>
                </HStack>
              )}

              {guided ? (
                <>
                  <HStack spacing={4} align={'start'}>
                    <FormControl flex={2} isInvalid={!!errors.host}>
                      <FormLabel>{t('host')}</FormLabel>
                      <Input {...register('host')} />
                      {errors.host && (
                        <FormErrorMessage>
                          {formErrorText(t, errors.host.message, 'required')}
                        </FormErrorMessage>
                      )}
                    </FormControl>
                    <FormControl flex={1}>
                      <FormLabel>{t('port')}</FormLabel>
                      <Input {...register('port')} />
                    </FormControl>
                  </HStack>
                  <FormControl>
                    <FormLabel>{t('database')}</FormLabel>
                    <Input {...register('database')} />
                    {errors.database && (
                      <FormErrorMessage>
                        {formErrorText(t, errors.database.message, 'required')}
                      </FormErrorMessage>
                    )}
                  </FormControl>
                  <FormControl>
                    <FormLabel>{t('urlPreview')}</FormLabel>
                    <Input
                      value={urlPreview}
                      isReadOnly
                      fontFamily={'monospace'}
                      fontSize={'sm'}
                      backgroundColor={colors.primary['200']}
                    />
                  </FormControl>
                </>
              ) : (
                <FormControl>
                  <FormLabel>{t('jdbcUrl')}</FormLabel>
                  <Input {...register('jdbcUrl')} fontFamily={'monospace'} />
                  {errors.jdbcUrl && (
                    <FormErrorMessage>
                      {formErrorText(t, errors.jdbcUrl.message, 'required')}
                    </FormErrorMessage>
                  )}
                </FormControl>
              )}

              <FormControl>
                <FormLabel>{t('driver')}</FormLabel>
                <Input
                  {...register('driverClass')}
                  fontFamily={'monospace'}
                  placeholder={'oracle.jdbc.OracleDriver'}
                />
              </FormControl>

              <HStack spacing={4} align={'start'}>
                <FormControl flex={1} isDisabled={clearPassword}>
                  <FormLabel>{t('password')}</FormLabel>
                  <Input type={'password'} {...register('password')} />
                  {initial?.passwordSet && !clearPassword && (
                    <FormHelperText>{t('passwordKeep')}</FormHelperText>
                  )}
                </FormControl>
                {initial?.passwordSet && (
                  <Checkbox mt={8} {...register('clearPassword')}>
                    {t('clearPassword')}
                  </Checkbox>
                )}
              </HStack>

              <FormControl>
                <FormLabel>{t('properties')}</FormLabel>
                <Input
                  {...register('propertiesJson')}
                  fontFamily={'monospace'}
                  placeholder={'{"useSSL": "false"}'}
                />
                {errors.propertiesJson && (
                  <FormErrorMessage>
                    {formErrorText(t, errors.propertiesJson.message, 'invalidJson')}
                  </FormErrorMessage>
                )}
              </FormControl>

              {formError && (
                <Text color={colors.failed['200']} fontSize={'sm'}>
                  {t('error', { message: formError })}
                </Text>
              )}
              {testResult && (
                <Text
                  color={
                    testFailed ? colors.failed['200'] : colors.success['200']
                  }
                  fontSize={'sm'}
                >
                  {testResult}
                </Text>
              )}
            </VStack>
          </ModalBody>
          <ModalFooter gap={2}>
            {initial && (
              <Button
                variant={'outline'}
                isLoading={test.isLoading}
                onClick={() => test.mutate()}
                mr={'auto'}
              >
                {t('test')}
              </Button>
            )}
            <Button variant={'ghost'} onClick={onClose}>
              {t('common:cancel')}
            </Button>
            <Button
              colorScheme={'blue'}
              type={'submit'}
              isLoading={save.isLoading}
            >
              {t('save')}
            </Button>
          </ModalFooter>
        </form>
      </ModalContent>
    </Modal>
  );
};

export default ConnectionForm;
