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
  Box,
  Button,
  FormControl,
  FormErrorMessage,
  FormLabel,
  HStack,
  IconButton,
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
  Tooltip,
  useToast,
  VStack,
} from '@chakra-ui/react';
import {
  AddIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  DragHandleIcon,
  SmallCloseIcon,
} from '@chakra-ui/icons';
import {
  closestCenter,
  DndContext,
  DragEndEvent,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
} from '@dnd-kit/core';
import { restrictToVerticalAxis } from '@dnd-kit/modifiers';
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Controller, useFieldArray, useForm } from 'react-hook-form';
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
import { Schema } from 'src/models/Schema';
import { getUsersEnabled } from 'src/utils/config';

function defaultValuesFor(
  initial?: SqlJob,
  singleSchemaId?: string | null,
): SqlJobFormValues {
  return {
    name: initial?.name ?? '',
    // a lone accessible schema is applied automatically (its picker is hidden);
    // editing a legacy schema-less job therefore assigns it to that schema
    schemaId: initial?.schemaId || singleSchemaId || '',
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
    scripts: initial?.scripts?.length
      ? initial.scripts.map((script) => ({ script }))
      : [{ script: '' }],
    // The backend stores 0 as "use server default"; show the default instead of an
    // invalid 0 so editing such a job does not fail validation.
    timeoutSeconds: String(initial?.timeoutSeconds || 300),
  };
}

function firstLine(script: string): string {
  return script.split('\n').find((line) => line.trim())?.trim() ?? '';
}

interface SortableScriptProps {
  id: string;
  dragLabel: string;
  header: React.ReactNode;
  trailing?: React.ReactNode;
  collapsed: boolean;
  onToggleCollapse: () => void;
  collapseLabel: string;
  expandLabel: string;
  /** First non-blank line, shown in place of the editor while collapsed. */
  preview?: string;
  error?: React.ReactNode;
  children: React.ReactNode;
}

/**
 * One script row, reorderable by dragging its grip handle. The drag listeners sit
 * only on the handle — attaching them to the row would swallow CodeMirror's own
 * mouse events (text selection, cursor placement).
 */
const SortableScript: React.FC<SortableScriptProps> = ({
  id,
  dragLabel,
  header,
  trailing,
  collapsed,
  onToggleCollapse,
  collapseLabel,
  expandLabel,
  preview,
  error,
  children,
}) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id });
  return (
    <Box
      ref={setNodeRef}
      style={{
        transform: CSS.Translate.toString(transform),
        transition,
        opacity: isDragging ? 0.7 : undefined,
        position: 'relative',
        zIndex: isDragging ? 1 : undefined,
      }}
    >
      <HStack justifyContent={'space-between'} mb={collapsed ? 0 : 1}>
        <HStack spacing={1}>
          <IconButton
            aria-label={dragLabel}
            icon={<DragHandleIcon boxSize={4} />}
            size={'sm'}
            variant={'ghost'}
            cursor={'grab'}
            {...attributes}
            {...listeners}
          />
          {header}
        </HStack>
        <HStack spacing={0}>
          <Tooltip label={collapsed ? expandLabel : collapseLabel}>
            <IconButton
              aria-label={collapsed ? expandLabel : collapseLabel}
              icon={
                collapsed ? (
                  <ChevronDownIcon boxSize={4} />
                ) : (
                  <ChevronUpIcon boxSize={4} />
                )
              }
              size={'sm'}
              variant={'ghost'}
              onClick={onToggleCollapse}
            />
          </Tooltip>
          {trailing}
        </HStack>
      </HStack>
      {collapsed ? (
        <Text fontSize={'xs'} color={'textMuted'} isTruncated>
          {preview || ' '}
        </Text>
      ) : (
        children
      )}
      {error}
    </Box>
  );
};

interface SqlJobFormProps {
  isOpen: boolean;
  onClose: () => void;
  connections: SqlConnection[];
  schemas: Schema[];
  initial?: SqlJob;
}

const SqlJobForm: React.FC<SqlJobFormProps> = ({
  isOpen,
  onClose,
  connections,
  schemas,
  initial,
}) => {
  const { t } = useTranslation('sql');
  const toast = useToast();
  const queryClient = useQueryClient();
  const [formError, setFormError] = useState<string | null>(null);
  // keyed on useFieldArray item ids — stable while the modal stays open
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});
  // with exactly one accessible schema the picker is hidden and the schema auto-filled
  const singleSchemaId =
    getUsersEnabled() && schemas.length === 1 ? schemas[0].id : null;

  const {
    register,
    handleSubmit,
    control,
    watch,
    reset,
    setValue,
    getValues,
    formState: { errors },
  } = useForm<SqlJobFormValues>({
    resolver: zodResolver(sqlJobFormSchema(getUsersEnabled())),
    defaultValues: defaultValuesFor(initial, singleSchemaId),
  });
  const { fields, append, remove, move } = useFieldArray({
    control,
    name: 'scripts',
  });
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } }),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  );
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) {
      return;
    }
    const from = fields.findIndex((item) => item.id === active.id);
    const to = fields.findIndex((item) => item.id === over.id);
    if (from >= 0 && to >= 0) {
      move(from, to);
    }
  };

  useEffect(() => {
    if (isOpen) {
      reset(defaultValuesFor(initial, singleSchemaId));
      setFormError(null);
      setCollapsed({});
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

  const usersEnabled = getUsersEnabled();
  const selectedSchema = watch('schemaId');
  // a job's connection must belong to the job's schema, so offer only matching
  // ones — but always keep the job's own connection visible, even when it does
  // not match (a legacy schema-less connection must not render as a blank field)
  const visibleConnections =
    usersEnabled && selectedSchema
      ? connections.filter(
          (c) => c.schemaId === selectedSchema || c.id === initial?.connectionId,
        )
      : connections;

  const onSchemaChange = (schemaId: string) => {
    setValue('schemaId', schemaId);
    const stillValid = connections.some(
      (c) =>
        c.id === watch('connectionId') &&
        (!usersEnabled || !schemaId || c.schemaId === schemaId),
    );
    if (!stillValid) {
      setValue('connectionId', '');
    }
  };

  // The schema list may arrive after the modal was opened — apply the auto-fill
  // with a targeted setValue so a late response cannot reset in-progress input.
  useEffect(() => {
    if (isOpen && singleSchemaId && !getValues('schemaId')) {
      onSchemaChange(singleSchemaId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isOpen, singleSchemaId]);

  const onSubmit = handleSubmit((values) => {
    setFormError(null);
    save.mutate({
      name: values.name.trim(),
      description: values.description.trim() || undefined,
      schemaId: values.schemaId || undefined,
      connectionId: values.connectionId,
      scripts: values.scripts.map((entry) => entry.script.trim()),
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

              {usersEnabled && !singleSchemaId && (
                <FormControl isInvalid={!!errors.schemaId}>
                  <FormLabel>{t('schema')}</FormLabel>
                  <Controller
                    control={control}
                    name="schemaId"
                    render={({ field }) => (
                      <Select
                        placeholder={t('selectSchema')}
                        value={field.value}
                        onChange={(event) => onSchemaChange(event.target.value)}
                      >
                        {schemas.map((schema) => (
                          <option key={schema.id} value={schema.id}>
                            {schema.name}
                          </option>
                        ))}
                      </Select>
                    )}
                  />
                  {errors.schemaId && (
                    <FormErrorMessage>
                      {formErrorText(t, errors.schemaId.message, 'schemaRequired')}
                    </FormErrorMessage>
                  )}
                </FormControl>
              )}

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
                      {visibleConnections.map((connection) => (
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

              <Box>
                <FormLabel>{t('script')}</FormLabel>
                <DndContext
                  sensors={sensors}
                  collisionDetection={closestCenter}
                  modifiers={[restrictToVerticalAxis]}
                  onDragEnd={handleDragEnd}
                >
                  <SortableContext
                    items={fields.map((item) => item.id)}
                    strategy={verticalListSortingStrategy}
                  >
                    <VStack spacing={3} align={'stretch'}>
                      {fields.map((item, index) => (
                        <SortableScript
                          key={item.id}
                          id={item.id}
                          dragLabel={t('dragToReorder')}
                          collapsed={!!collapsed[item.id]}
                          onToggleCollapse={() =>
                            setCollapsed((prev) => ({
                              ...prev,
                              [item.id]: !prev[item.id],
                            }))
                          }
                          collapseLabel={t('collapseScript')}
                          expandLabel={t('expandScript')}
                          preview={firstLine(item.script)}
                          error={
                            errors.scripts?.[index]?.script ? (
                              // FormErrorMessage renders nothing outside a FormControl
                              <FormControl isInvalid>
                                <FormErrorMessage>
                                  {formErrorText(
                                    t,
                                    errors.scripts[index]?.script?.message,
                                    'scriptRequired',
                                  )}
                                </FormErrorMessage>
                              </FormControl>
                            ) : undefined
                          }
                          header={
                            <Text fontSize={'sm'} fontWeight={'semibold'}>
                              {t('scriptNumber', { number: index + 1 })}
                            </Text>
                          }
                          trailing={
                            fields.length > 1 ? (
                              <Tooltip label={t('removeScript')}>
                                <IconButton
                                  aria-label={t('removeScript')}
                                  icon={<SmallCloseIcon boxSize={4} />}
                                  size={'sm'}
                                  variant={'ghost'}
                                  onClick={() => remove(index)}
                                />
                              </Tooltip>
                            ) : undefined
                          }
                        >
                          <Controller
                            control={control}
                            name={`scripts.${index}.script`}
                            render={({ field }) => (
                              <ScriptEditor
                                value={field.value}
                                onChange={field.onChange}
                                placeholder={'SELECT 1;'}
                              />
                            )}
                          />
                        </SortableScript>
                      ))}
                    </VStack>
                  </SortableContext>
                </DndContext>
                <Button
                  mt={2}
                  size={'sm'}
                  variant={'outline'}
                  leftIcon={<AddIcon boxSize={3} />}
                  onClick={() => append({ script: '' })}
                >
                  {t('addScript')}
                </Button>
              </Box>

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
