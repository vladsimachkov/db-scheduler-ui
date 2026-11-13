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
  Box,
  Button,
  Menu,
  MenuButton,
  MenuItem,
  MenuList,
  Text,
} from '@chakra-ui/react';
import React from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { LogoIcon } from 'src/assets/icons/Logo';
import { ColorModeToggle } from 'src/components/common/ColorModeToggle';
import colors from 'src/styles/colors';
import {
  getShowHistory,
  getShowOverview,
  getShowSql,
} from 'src/utils/config';

interface TopBarProps {
  title: string;
}

export const TopBar: React.FC<TopBarProps> = ({ title }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const showHistory = getShowHistory();
  const showOverview = getShowOverview();
  const showSql = getShowSql();
  const showNavigation = showOverview || showHistory || showSql;
  const isHistory = location.pathname.startsWith('/history');
  const isOverview = showOverview && location.pathname === '/overview';
  const isSql = showSql && location.pathname.startsWith('/sql-jobs');
  const isConnections = showSql && location.pathname === '/sql-connections';
  const isScheduled =
    !isHistory && !isOverview && !isSql && !isConnections;
  const scheduledPath = showOverview ? '/scheduled' : '/';

  return (
    <Box
      backgroundColor={colors.primary['100']}
      display={'flex'}
      alignItems={'center'}
      flexWrap={'wrap'}
      gap={'12'}
    >
      <Text
        as={'button'}
        onClick={() => navigate('/')}
        aria-label={t('common:nav.homeAria')}
        color={colors.dbBlue}
        fontSize={'2xl'}
        p={4}
        fontWeight={'semibold'}
      >
        <LogoIcon mr={2} />
        {title}
      </Text>
      <Box
        display={'flex'}
        alignItems={'center'}
        flexWrap={'wrap'}
        gap={12}
      >
        {showNavigation && (
          <>
            <Button
              _hover={{
                bgColor: colors.primary['100'],
                borderColor: colors.dbBlue,
                color: colors.primary['400'],
              }}
              _active={{
                borderColor: colors.primary['200'],
                color: colors.primary['300'],
              }}
              bgColor={colors.primary['100']}
              color={colors.dbBlue}
              borderBottom="2px"
              borderRadius={'0'}
              borderColor={isScheduled ? colors.dbBlue : colors.primary['300']}
              onClick={() => navigate(scheduledPath)}
              aria-label={t('common:nav.scheduledAria')}
            >
              {t('common:nav.scheduled')}
            </Button>
            {showHistory && (
              <Button
                _hover={{
                  bgColor: colors.primary['100'],
                  borderColor: colors.dbBlue,
                  color: colors.primary['400'],
                }}
                _active={{
                  borderColor: colors.running['200'],
                  color: colors.primary['300'],
                }}
                bgColor={colors.primary['100']}
                color={colors.dbBlue}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={isHistory ? colors.dbBlue : colors.primary['300']}
                onClick={() => navigate(`/history/all`)}
                aria-label={t('common:nav.historyAria')}
              >
                {t('common:nav.history')}
              </Button>
            )}
            {showOverview && (
              <Button
                _hover={{
                  bgColor: colors.primary['100'],
                  borderColor: colors.dbBlue,
                  color: colors.primary['400'],
                }}
                _active={{
                  borderColor: colors.primary['200'],
                  color: colors.primary['300'],
                }}
                bgColor={colors.primary['100']}
                color={colors.dbBlue}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={isOverview ? colors.dbBlue : colors.primary['300']}
                onClick={() => navigate('/overview')}
                aria-label={t('common:nav.overviewAria')}
              >
                {t('common:nav.overview')}
              </Button>
            )}
            {showSql && (
              <Button
                _hover={{
                  bgColor: colors.primary['100'],
                  borderColor: colors.dbBlue,
                  color: colors.primary['400'],
                }}
                _active={{
                  borderColor: colors.primary['200'],
                  color: colors.primary['300'],
                }}
                bgColor={colors.primary['100']}
                color={colors.dbBlue}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={isSql ? colors.dbBlue : colors.primary['300']}
                onClick={() => navigate('/sql-jobs')}
                aria-label={t('common:nav.sqlAria')}
              >
                {t('common:nav.sql')}
              </Button>
            )}
            {showSql && (
              <Button
                _hover={{
                  bgColor: colors.primary['100'],
                  borderColor: colors.dbBlue,
                  color: colors.primary['400'],
                }}
                _active={{
                  borderColor: colors.primary['200'],
                  color: colors.primary['300'],
                }}
                bgColor={colors.primary['100']}
                color={colors.dbBlue}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={
                  isConnections ? colors.dbBlue : colors.primary['300']
                }
                onClick={() => navigate('/sql-connections')}
                aria-label={t('common:nav.connectionsAria')}
              >
                {t('common:nav.connections')}
              </Button>
            )}
          </>
        )}
        <Menu>
          <MenuButton
            as={Button}
            variant="ghost"
            color={colors.dbBlue}
            aria-label={t('common:language.switcher')}
          >
            {i18n.language?.startsWith('ru') ? 'RU' : 'EN'}
          </MenuButton>
          <MenuList>
            <MenuItem onClick={() => i18n.changeLanguage('ru')}>
              {t('common:language.ru')}
            </MenuItem>
            <MenuItem onClick={() => i18n.changeLanguage('en')}>
              {t('common:language.en')}
            </MenuItem>
          </MenuList>
        </Menu>
        <ColorModeToggle />
      </Box>
    </Box>
  );
};
