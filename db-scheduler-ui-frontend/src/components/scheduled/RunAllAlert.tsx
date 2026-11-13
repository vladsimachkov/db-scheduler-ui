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
import runTaskGroup from 'src/services/runTaskGroup';
import colors from 'src/styles/colors';

interface TaskProps {
  onlyFailed: boolean;
  taskName: string;
  style?: React.CSSProperties;
  isOpen: boolean;
  setIsopen: React.Dispatch<React.SetStateAction<string>>;
  refetch: () => void;
}

export const RunAllAlert: React.FC<TaskProps> = ({
  onlyFailed,
  taskName,
  isOpen,
  setIsopen,
  refetch,
}) => {
  const { t } = useTranslation();
  const cancelRef = React.useRef(null);
  return (
    <AlertDialog
      isOpen={isOpen}
      leastDestructiveRef={cancelRef}
      onClose={() => setIsopen('')}
    >
      <AlertDialogOverlay>
        <AlertDialogContent>
          <AlertDialogHeader fontSize="lg" fontWeight="bold">
            {onlyFailed
              ? t('scheduled:runAllAlert.titleRerunFailed')
              : t('scheduled:runAllAlert.titleRun')}
          </AlertDialogHeader>

          <AlertDialogBody>
            {onlyFailed
              ? t('scheduled:runAllAlert.bodyRerunFailed', { taskName })
              : t('scheduled:runAllAlert.bodyRun', { taskName })}
          </AlertDialogBody>

          <AlertDialogFooter>
            <Button
              onClick={() => {
                setIsopen('');
              }}
            >
              {t('common:cancel')}
            </Button>
            <Button
              bgColor={onlyFailed ? colors.running[300] : colors.running[100]}
              _hover={{
                backgroundColor: onlyFailed
                  ? colors.running[200]
                  : colors.running[100],
              }}
              _active={{
                backgroundColor: onlyFailed
                  ? colors.running[200]
                  : colors.running[300],
              }}
              textColor={onlyFailed ? colors.primary[100] : colors.running[500]}
              onClick={() => {
                runTaskGroup(taskName, onlyFailed).then(() => {
                  refetch();
                });
                setIsopen('');
              }}
              ml={3}
            >
              {onlyFailed
                ? t('scheduled:runAllAlert.buttonRerunFailed')
                : t('scheduled:runAllAlert.buttonRun')}
            </Button>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialogOverlay>
    </AlertDialog>
  );
};
