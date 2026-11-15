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
  Box,
  Checkbox,
  HStack,
  Input,
  Select,
  Text,
  VStack,
  Button,
} from '@chakra-ui/react';
import { FilterBy } from 'src/models/QueryParams';
import { FilterBox } from 'src/components/input/FilterBox';
import { RefreshButton } from 'src/components/input/RefreshButton';
import { QueryObserverResult, InfiniteData } from '@tanstack/react-query';
import { InfiniteScrollResponse } from 'src/models/TasksResponse';
import { Log } from 'src/models/Log';
import { Task } from 'src/models/Task';
import { POLL_LOGS_QUERY_KEY, pollLogs } from 'src/services/pollLogs';
import { POLL_TASKS_QUERY_KEY, pollTasks } from 'src/services/pollTasks';
import { PlayIcon, RepeatIcon } from 'src/assets/icons';
import { RunAllAlert } from 'src/components/scheduled/RunAllAlert';
import { TaskDetailsRequestParams } from 'src/models/TaskRequestParams';
import { useParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useDebouncedCallback } from 'src/hooks/useDebouncedCallback';
import { Schema } from 'src/models/Schema';

interface HeaderBarProps {
  params: TaskDetailsRequestParams;
  setCurrentFilter: (filter: FilterBy) => void;
  setSearchTermTaskName: (searchTerm: string) => void;
  setSearchTermTaskInstance: (searchTerm: string) => void;
  refetch?: () => Promise<
    QueryObserverResult<InfiniteData<InfiniteScrollResponse<Task | Log>>>
  >;
  title: string;
  history?: boolean;
  setTaskNameExactMatch: (exactMatch: boolean) => void;
  setTaskInstanceExactMatch: (exactMatch: boolean) => void;
  taskNameExactMatch: boolean;
  taskInstanceExactMatch: boolean;
  // schema filter, only rendered when more than one schema is accessible
  schemaOptions?: Schema[];
  schemaId?: string;
  onSchemaIdChange?: (schemaId: string) => void;
}

export const HeaderBar: React.FC<HeaderBarProps> = ({
  setCurrentFilter,
  setSearchTermTaskName,
  setSearchTermTaskInstance,
  refetch,
  title,
  history,
  setTaskNameExactMatch,
  setTaskInstanceExactMatch,
  taskNameExactMatch,
  taskInstanceExactMatch,
  schemaOptions,
  schemaId,
  onSchemaIdChange,
  params,
}) => {
  const { t } = useTranslation();
  const { taskName: urlTaskName } = useParams<{ taskName?: string }>();
  const isDetailsView = !!urlTaskName;
  const [isOpen, setIsOpen] = React.useState('');
  const { taskName, taskId: taskInstance, filter: currentFilter } = params;
  const debouncedSetSearchTermTaskName = useDebouncedCallback(
    setSearchTermTaskName,
  );
  const debouncedSetSearchTermTaskInstance = useDebouncedCallback(
    setSearchTermTaskInstance,
  );

  return (
    <Box
      display={'flex'}
      mb={7}
      alignItems={'center'}
      justifyContent={'space-between'}
      w={'100%'}
    >
      <Box display={'flex'} alignItems={'center'} flex={1}>
        <Box>
          <Box display={'flex'}>
            <Text ml={1} fontSize={'3xl'} fontWeight={'semibold'}>
              {title}
            </Text>
            {taskName && isDetailsView && (
              <>
                <Button
                  leftIcon={<PlayIcon />}
                  bgColor={'runningFill'}
                  textColor={'runningText'}
                  _hover={{ backgroundColor: 'runningFillHover' }}
                  _active={{ backgroundColor: 'runningFill' }}
                  ml={5}
                  minW={'6em'}
                  onClick={() => {
                    setIsOpen('scheduled');
                  }}
                >
                  {t('scheduled:headerBar.runAll')}
                </Button>
                <Button
                  leftIcon={<RepeatIcon boxSize={6} />}
                  bgColor={'runningSolid'}
                  textColor={'textOnSolid'}
                  _hover={{ backgroundColor: 'runningSolidHover' }}
                  _active={{ backgroundColor: 'runningSolid' }}
                  mx={5}
                  minW={'10em'}
                  onClick={() => {
                    setIsOpen('failed');
                  }}
                >
                  {t('scheduled:headerBar.rerunAllFailed')}
                </Button>
                <RunAllAlert
                  taskName={taskName}
                  isOpen={!!isOpen}
                  setIsopen={setIsOpen}
                  onlyFailed={isOpen === 'failed'}
                  refetch={refetch ?? (() => {})}
                />
              </>
            )}
          </Box>
          <HStack>
            {!isDetailsView && (
              <VStack align="start">
                <Input
                  placeholder={t('scheduled:search.taskName')}
                  defaultValue={taskName}
                  onChange={(e) =>
                    debouncedSetSearchTermTaskName(e.currentTarget.value)
                  }
                  bgColor={'surface'}
                  w={'20vmax'}
                  mt={7}
                  ml={1}
                />
                <Checkbox
                  ml={1}
                  isChecked={taskNameExactMatch}
                  onChange={(e) => setTaskNameExactMatch(e.target.checked)}
                  sx={{
                    '.chakra-checkbox__control': {
                      bg: 'surface',
                      _checked: {
                        bg: 'controlChecked',
                        borderColor: 'controlChecked',
                      },
                    },
                  }}
                >
                  {t('scheduled:search.exactMatch')}
                </Checkbox>
              </VStack>
            )}
            <VStack align="start" spacing={2}>
              <Input
                placeholder={t('scheduled:search.taskId')}
                defaultValue={taskInstance}
                onChange={(e) =>
                  debouncedSetSearchTermTaskInstance(e.currentTarget.value)
                }
                bgColor={'surface'}
                w={'20vmax'}
                mt={7}
                ml={1}
              />
              <Checkbox
                ml={1}
                isChecked={taskInstanceExactMatch}
                onChange={(e) => setTaskInstanceExactMatch(e.target.checked)}
                sx={{
                  '.chakra-checkbox__control': {
                    bg: 'surface',
                    _checked: {
                      bg: 'controlChecked',
                      borderColor: 'controlChecked',
                    },
                  },
                }}
              >
                {t('scheduled:search.exactMatch')}
              </Checkbox>
            </VStack>
          </HStack>
        </Box>
      </Box>
      <Box alignSelf={'start'}>
        {/* a single-task history view (taskName via location.state) is details too:
            filtering it by schema would empty it for host tasks */}
        {!isDetailsView && !taskName && schemaOptions && schemaOptions.length > 1 && (
          <Select
            float={'left'}
            mr={3}
            w={'auto'}
            maxW={'240px'}
            aria-label={t('sql:schema')}
            value={schemaId ?? ''}
            onChange={(event) => onSchemaIdChange?.(event.target.value)}
          >
            <option value="">{t('sql:allSchemas')}</option>
            {schemaOptions.map((schema) => (
              <option key={schema.id} value={schema.id}>
                {schema.name}
              </option>
            ))}
          </Select>
        )}
        <FilterBox
          currentFilter={currentFilter}
          setCurrentFilter={setCurrentFilter}
          history={history}
        />
        <Box display={'flex'} float={'right'} alignItems={'center'}>
          <RefreshButton
            pollFunction={history ? pollLogs : pollTasks}
            pollKey={history ? POLL_LOGS_QUERY_KEY : POLL_TASKS_QUERY_KEY}
            refetch={refetch}
            params={params}
          />
        </Box>
      </Box>
    </Box>
  );
};
