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
import {
  AlertDialog,
  AlertDialogBody,
  AlertDialogContent,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogOverlay,
  Button,
} from '@chakra-ui/react';
import React from 'react';
import { useTranslation } from 'react-i18next';
import { DateTimeInput } from './DateTimeInput';
import runTask from 'src/services/runTask';

interface TaskProps {
  failed: boolean;
  taskName: string;
  taskId: string;
  isOpen: boolean;
  setIsopen: React.Dispatch<React.SetStateAction<boolean>>;
  refetch: () => void;
}

export const ScheduleRunAlert: React.FC<TaskProps> = ({
  failed,
  isOpen,
  taskId,
  taskName,
  setIsopen,
  refetch,
}) => {
  const { t } = useTranslation();
  const cancelRef = React.useRef(null);
  const [time, setTime] = React.useState<Date | null>(new Date());

  return (
    <AlertDialog
      isOpen={isOpen}
      leastDestructiveRef={cancelRef}
      onClose={() => setIsopen(false)}
    >
      <AlertDialogOverlay>
        <AlertDialogContent>
          <AlertDialogHeader fontSize="lg" fontWeight="bold">
            {failed
              ? t('scheduled:scheduleRunAlert.titleRerun')
              : t('scheduled:scheduleRunAlert.titleRun')}
          </AlertDialogHeader>

          <AlertDialogBody p={4}>
            <DateTimeInput
              onChange={(e) => setTime(e)}
              selectedDate={time}
              forceFutureTime
            />
          </AlertDialogBody>

          <AlertDialogFooter justifyContent={'space-between'}>
            <Button
              onClick={() => {
                setIsopen(false);
              }}
            >
              {t('common:cancel')}
            </Button>
            <Button
              bgColor={failed ? 'runningSolid' : 'runningFill'}
              _hover={{
                backgroundColor: failed
                  ? 'runningFillHover'
                  : 'runningFill',
              }}
              _active={{
                backgroundColor: failed
                  ? 'runningFillHover'
                  : 'runningSolid',
              }}
              textColor={failed ? 'textOnSolid' : 'runningText'}
              onClick={() => {
                time &&
                  runTask(taskId, taskName, time).then(() => {
                    refetch();
                  });
                setIsopen(false);
              }}
              ml={3}
            >
              {failed
                ? t('scheduled:scheduleRunAlert.buttonRerun')
                : t('scheduled:scheduleRunAlert.buttonRun')}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialogOverlay>
    </AlertDialog>
  );
};
