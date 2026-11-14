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
  FormControl,
  FormErrorMessage,
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
  Radio,
  RadioGroup,
  Select,
  Stack,
  Text,
  useToast,
  VStack,
} from '@chakra-ui/react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useTranslation } from 'react-i18next';
import CronInput from 'src/components/sql/CronInput';
import {
  formErrorText,
  SqlJobFormValues,
  sqlJobFormSchema,
} from 'src/components/sql/formSchemas';
import ScriptEditor from 'src/components/sql/ScriptEditor';
import { DateTimeInput } from 'src/components/input/DateTimeInput';
import { SqlConnection } from 'src/models/SqlConnection';
import { SqlJob, SqlJobRequest } from 'src/models/SqlJob';
import { JOBS_QUERY_KEY, createJob, updateJob } from 'src/services/sqlJobs';

function defaultValuesFor(initial?: SqlJob): SqlJobFormValues {
  return {
    name: initial?.name ?? '',
    description: initial?.description ?? '',
    connectionId: initial?.connectionId ?? '',
    scheduleType: initial && !initial.recurring ? 'once' : 'cron',
    cronExpression: initial?.cronExpression ?? '',
    // A pending one-time job's next run is its scheduled time — prefill so editing
    // the script or name does not force re-picking a time.
    runAt:
      initial && !initial.recurring && initial.nextRunTime
        ? new Date(initial.nextRunTime)
        : null,
    script: initial?.script ?? '',
    // The backend stores 0 as "use server default"; show the default instead of an
    // invalid 0 so editing such a job does not fail validation.
    timeoutSeconds: String(initial?.timeoutSeconds || 300),
  };
}

interface SqlJobFormProps {
  isOpen: boolean;
  onClose: () => void;
  connections: SqlConnection[];
  initial?: SqlJob;
}

const SqlJobForm: React.FC<SqlJobFormProps> = ({
  isOpen,
  onClose,
  connections,
  initial,
}) => {
  const { t } = useTranslation('sql');
  const toast = useToast();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    control,
    watch,
    reset,
    formState: { errors },
  } = useForm<SqlJobFormValues>({
    resolver: zodResolver(sqlJobFormSchema),
    defaultValues: defaultValuesFor(initial),
  });

  useEffect(() => {
    if (isOpen) {
      reset(defaultValuesFor(initial));
      setFormError(null);
    }
    // Keyed on the stable id, not the object: polling hands us a fresh SqlJob
    // instance every 2s, which would otherwise wipe in-progress edits.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, initial?.jobId]);

  const scheduleType = watch('scheduleType');

  const save = useMutation({
    mutationFn: (payload: SqlJobRequest) =>
      initial ? updateJob(initial.jobId, payload) : createJob(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [JOBS_QUERY_KEY] });
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

  const onSubmit = handleSubmit((values) => {
    setFormError(null);
    save.mutate({
      name: values.name.trim(),
      description: values.description.trim() || undefined,
      connectionId: values.connectionId,
      script: values.script,
      timeoutSeconds: Number(values.timeoutSeconds),
      cronExpression:
        values.scheduleType === 'cron'
          ? values.cronExpression.trim()
          : undefined,
      runAt:
        values.scheduleType === 'once' && values.runAt
          ? values.runAt.toISOString()
          : undefined,
    });
  });

  return (
    <Modal isOpen={isOpen} onClose={onClose} size={'xl'}>
      <ModalOverlay />
      <ModalContent>
        <form onSubmit={onSubmit}>
          <ModalHeader>{initial ? t('edit') : t('create')}</ModalHeader>
          <ModalCloseButton />
          <ModalBody>
            <VStack spacing={4} align={'stretch'}>
              <HStack spacing={4} align={'start'}>
                <FormControl flex={1} isInvalid={!!errors.name}>
                  <FormLabel>{t('name')}</FormLabel>
                  <Input {...register('name')} />
                  {errors.name && (
                    <FormErrorMessage>
                      {formErrorText(t, errors.name.message, 'required')}
                    </FormErrorMessage>
                  )}
                </FormControl>
                <FormControl flex={1} isInvalid={!!errors.timeoutSeconds}>
                  <FormLabel>{t('timeout')}</FormLabel>
                  <Input {...register('timeoutSeconds')} inputMode={'numeric'} />
                  {errors.timeoutSeconds && (
                    <FormErrorMessage>
                      {formErrorText(t, errors.timeoutSeconds.message, 'timeoutInvalid')}
                    </FormErrorMessage>
                  )}
                </FormControl>
              </HStack>

              <FormControl isInvalid={!!errors.description}>
                <FormLabel>{t('description')}</FormLabel>
                <Input {...register('description')} />
              </FormControl>

              <FormControl isInvalid={!!errors.connectionId}>
                <FormLabel>{t('connection')}</FormLabel>
                <Controller
                  control={control}
                  name="connectionId"
                  render={({ field }) => (
                    <Select
                      placeholder={t('connectionRequired')}
                      value={field.value}
                      onChange={field.onChange}
                    >
                      {connections.map((connection) => (
                        <option key={connection.id} value={connection.id}>
                          {connection.name}
                        </option>
                      ))}
                    </Select>
                  )}
                />
                {errors.connectionId && (
                  <FormErrorMessage>
                    {formErrorText(t, errors.connectionId.message, 'connectionRequired')}
                  </FormErrorMessage>
                )}
              </FormControl>

              <FormControl>
                <FormLabel>{t('scheduleType')}</FormLabel>
                <Controller
                  control={control}
                  name="scheduleType"
                  render={({ field }) => (
                    <RadioGroup value={field.value} onChange={field.onChange}>
                      <Stack direction={'row'}>
                        <Radio value={'cron'}>{t('scheduleCron')}</Radio>
                        <Radio value={'once'}>{t('scheduleOnce')}</Radio>
                      </Stack>
                    </RadioGroup>
                  )}
                />
              </FormControl>

              {scheduleType === 'cron' ? (
                <>
                  <FormControl isInvalid={!!errors.cronExpression}>
                    <FormLabel>{t('cron')}</FormLabel>
                    <Controller
                      control={control}
                      name="cronExpression"
                      render={({ field }) => (
                        <CronInput
                          value={field.value}
                          onChange={field.onChange}
                          isInvalid={!!errors.cronExpression}
                          errorMessage={formErrorText(
                            t,
                            errors.cronExpression?.message,
                            'required',
                          )}
                        />
                      )}
                    />
                  </FormControl>
                </>
              ) : (
                <FormControl isInvalid={!!errors.runAt}>
                  <FormLabel>{t('runAt')}</FormLabel>
                  <Controller
                    control={control}
                    name="runAt"
                    render={({ field }) => (
                      <DateTimeInput
                        selectedDate={field.value}
                        onChange={field.onChange}
                        forceFutureTime
                      />
                    )}
                  />
                  {errors.runAt && (
                    <FormErrorMessage>
                      {formErrorText(t, errors.runAt.message, 'runAtRequired')}
                    </FormErrorMessage>
                  )}
                </FormControl>
              )}

              <FormControl isInvalid={!!errors.script}>
                <FormLabel>{t('script')}</FormLabel>
                <Controller
                  control={control}
                  name="script"
                  render={({ field }) => (
                    <ScriptEditor
                      value={field.value}
                      onChange={field.onChange}
                      placeholder={'SELECT 1;'}
                    />
                  )}
                />
                {errors.script && (
                  <FormErrorMessage>
                    {formErrorText(t, errors.script.message, 'scriptRequired')}
                  </FormErrorMessage>
                )}
              </FormControl>

              {formError && (
                <Text color="failed" fontSize={'sm'}>
                  {t('error', { message: formError })}
                </Text>
              )}
            </VStack>
          </ModalBody>
          <ModalFooter gap={2}>
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

export default SqlJobForm;
