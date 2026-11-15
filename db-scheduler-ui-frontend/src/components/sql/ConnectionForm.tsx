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
  Text,
  useToast,
  VStack,
} from '@chakra-ui/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import {
  connectionFormSchema,
  ConnectionFormValues,
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
import { useSchemas } from 'src/hooks/useSchemas';
import { getUsersEnabled } from 'src/utils/config';

const DIALECTS: SqlDialect[] = [
  'ORACLE',
  'TRINO',
  'IMPALA',
  'MYSQL',
  'MARIADB',
  'POSTGRESQL',
  'MSSQL',
  'CLICKHOUSE',
  'GENERIC',
];

// Driver class is optional (Hikari auto-discovers from the classpath); the placeholder
// just shows the canonical class for the selected dialect. GENERIC has no single driver.
const DIALECT_DRIVER_HINT: Record<SqlDialect, string> = {
  ORACLE: 'oracle.jdbc.OracleDriver',
  TRINO: 'io.trino.jdbc.TrinoDriver',
  IMPALA: 'com.cloudera.impala.jdbc41.Driver',
  MYSQL: 'com.mysql.cj.jdbc.Driver',
  MARIADB: 'org.mariadb.jdbc.Driver',
  POSTGRESQL: 'org.postgresql.Driver',
  MSSQL: 'com.microsoft.sqlserver.jdbc.SQLServerDriver',
  CLICKHOUSE: 'com.clickhouse.jdbc.ClickHouseDriver',
  GENERIC: '',
};

function defaultValuesFor(initial?: SqlConnection): ConnectionFormValues {
  return {
    name: initial?.name ?? '',
    schemaId: initial?.schemaId ?? '',
    dialect: initial?.dialect ?? 'ORACLE',
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
  const usersEnabled = getUsersEnabled();
  const { data: schemas, isError: schemasError } = useSchemas();
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
    reset,
    formState: { errors },
  } = useForm<ConnectionFormValues>({
    resolver: zodResolver(connectionFormSchema(usersEnabled)),
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
  const clearPassword = values.clearPassword;

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
      schemaId: formValues.schemaId || undefined,
      dialect: formValues.dialect,
      jdbcUrl: formValues.jdbcUrl.trim(),
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

              {usersEnabled && (
                <FormControl isInvalid={!!errors.schemaId}>
                  <FormLabel>{t('schema')}</FormLabel>
                  <Controller
                    control={control}
                    name="schemaId"
                    render={({ field }) => (
                      <Select
                        placeholder={t('selectSchema')}
                        value={field.value}
                        onChange={field.onChange}
                      >
                        {(schemas ?? []).map((schema) => (
                          <option key={schema.id} value={schema.id}>
                            {schema.name}
                          </option>
                        ))}
                      </Select>
                    )}
                  />
                  {schemasError && (
                    <Text fontSize="sm" color="failed">
                      {t('schemasError')}
                    </Text>
                  )}
                  {errors.schemaId && (
                    <FormErrorMessage>
                      {formErrorText(t, errors.schemaId.message, 'schemaRequired')}
                    </FormErrorMessage>
                  )}
                </FormControl>
              )}

              <HStack spacing={4} align={'start'}>
                <FormControl flex={1}>
                  <FormLabel>{t('dialect')}</FormLabel>
                  <Controller
                    control={control}
                    name="dialect"
                    render={({ field }) => (
                      <Select
                        value={field.value}
                        onChange={(event) =>
                          field.onChange(event.target.value as SqlDialect)
                        }
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

              <FormControl isInvalid={!!errors.jdbcUrl}>
                <FormLabel>{t('jdbcUrl')}</FormLabel>
                <Input {...register('jdbcUrl')} fontFamily={'monospace'} />
                {errors.jdbcUrl && (
                  <FormErrorMessage>
                    {formErrorText(t, errors.jdbcUrl.message, 'required')}
                  </FormErrorMessage>
                )}
              </FormControl>

              <FormControl>
                <FormLabel>{t('driver')}</FormLabel>
                <Input
                  {...register('driverClass')}
                  fontFamily={'monospace'}
                  placeholder={DIALECT_DRIVER_HINT[values.dialect] || undefined}
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

              <FormControl isInvalid={!!errors.propertiesJson}>
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
                <Text color="failed" fontSize={'sm'}>
                  {t('error', { message: formError })}
                </Text>
              )}
              {testResult && (
                <Text
                  color={
                    testFailed ? 'failed' : 'success'
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
