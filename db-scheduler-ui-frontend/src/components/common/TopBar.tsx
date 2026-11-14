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
import { useAuth } from 'src/hooks/useAuth';
import {
  getReadonly,
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
  const { t: tAuth } = useTranslation('auth');
  const { t: tAdmin } = useTranslation('admin');
  const { usersEnabled, username, admin, logout } = useAuth();
  const showHistory = getShowHistory();
  const showOverview = getShowOverview();
  const showSql = getShowSql();
  // the admin endpoints do not exist in a read-only deployment — no point offering the tabs
  const readOnly = getReadonly();
  const showNavigation = showOverview || showHistory || showSql;
  const isHistory = location.pathname.startsWith('/history');
  const isOverview = showOverview && location.pathname === '/overview';
  const isSql = showSql && location.pathname.startsWith('/sql-jobs');
  const isConnections = showSql && location.pathname === '/sql-connections';
  const isAdminUsers = location.pathname.startsWith('/admin/users');
  const isAdminSchemas = location.pathname.startsWith('/admin/schemas');
  const isScheduled =
    !isHistory &&
    !isOverview &&
    !isSql &&
    !isConnections &&
    !isAdminUsers &&
    !isAdminSchemas;
  const scheduledPath = showOverview ? '/scheduled' : '/';

  return (
    <Box
      backgroundColor={'surface'}
      display={'flex'}
      alignItems={'center'}
      flexWrap={'wrap'}
      gap={'12'}
    >
      <Text
        as={'button'}
        onClick={() => navigate('/')}
        aria-label={t('common:nav.homeAria')}
        color={'brand'}
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
                bgColor: 'surface',
                borderColor: 'brand',
                color: 'textMuted',
              }}
              _active={{
                borderColor: 'surfaceSubtle',
                color: 'textFaint',
              }}
              bgColor={'surface'}
              color={'brand'}
              borderBottom="2px"
              borderRadius={'0'}
              borderColor={isScheduled ? 'brand' : 'border'}
              onClick={() => navigate(scheduledPath)}
              aria-label={t('common:nav.scheduledAria')}
            >
              {t('common:nav.scheduled')}
            </Button>
            {showHistory && (
              <Button
                _hover={{
                  bgColor: 'surface',
                  borderColor: 'brand',
                  color: 'textMuted',
                }}
                _active={{
                  borderColor: 'runningFillHover',
                  color: 'textFaint',
                }}
                bgColor={'surface'}
                color={'brand'}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={isHistory ? 'brand' : 'border'}
                onClick={() => navigate(`/history/all`)}
                aria-label={t('common:nav.historyAria')}
              >
                {t('common:nav.history')}
              </Button>
            )}
            {showOverview && (
              <Button
                _hover={{
                  bgColor: 'surface',
                  borderColor: 'brand',
                  color: 'textMuted',
                }}
                _active={{
                  borderColor: 'surfaceSubtle',
                  color: 'textFaint',
                }}
                bgColor={'surface'}
                color={'brand'}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={isOverview ? 'brand' : 'border'}
                onClick={() => navigate('/overview')}
                aria-label={t('common:nav.overviewAria')}
              >
                {t('common:nav.overview')}
              </Button>
            )}
            {showSql && (
              <Button
                _hover={{
                  bgColor: 'surface',
                  borderColor: 'brand',
                  color: 'textMuted',
                }}
                _active={{
                  borderColor: 'surfaceSubtle',
                  color: 'textFaint',
                }}
                bgColor={'surface'}
                color={'brand'}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={isSql ? 'brand' : 'border'}
                onClick={() => navigate('/sql-jobs')}
                aria-label={t('common:nav.sqlAria')}
              >
                {t('common:nav.sql')}
              </Button>
            )}
            {showSql && (
              <Button
                _hover={{
                  bgColor: 'surface',
                  borderColor: 'brand',
                  color: 'textMuted',
                }}
                _active={{
                  borderColor: 'surfaceSubtle',
                  color: 'textFaint',
                }}
                bgColor={'surface'}
                color={'brand'}
                borderBottom="2px"
                borderRadius={'0'}
                borderColor={
                  isConnections ? 'brand' : 'border'
                }
                onClick={() => navigate('/sql-connections')}
                aria-label={t('common:nav.connectionsAria')}
              >
                {t('common:nav.connections')}
              </Button>
            )}
            {showSql && usersEnabled && admin && !readOnly && (
              <>
                <Button
                  _hover={{
                    bgColor: 'surface',
                    borderColor: 'brand',
                    color: 'textMuted',
                  }}
                  _active={{
                    borderColor: 'surfaceSubtle',
                    color: 'textFaint',
                  }}
                  bgColor={'surface'}
                  color={'brand'}
                  borderBottom="2px"
                  borderRadius={'0'}
                  borderColor={isAdminUsers ? 'brand' : 'border'}
                  onClick={() => navigate('/admin/users')}
                  aria-label={tAdmin('navUsers')}
                >
                  {tAdmin('navUsers')}
                </Button>
                <Button
                  _hover={{
                    bgColor: 'surface',
                    borderColor: 'brand',
                    color: 'textMuted',
                  }}
                  _active={{
                    borderColor: 'surfaceSubtle',
                    color: 'textFaint',
                  }}
                  bgColor={'surface'}
                  color={'brand'}
                  borderBottom="2px"
                  borderRadius={'0'}
                  borderColor={isAdminSchemas ? 'brand' : 'border'}
                  onClick={() => navigate('/admin/schemas')}
                  aria-label={tAdmin('navSchemas')}
                >
                  {tAdmin('navSchemas')}
                </Button>
              </>
            )}
          </>
        )}
        {usersEnabled && username && (
          <>
            <Text color={'textMuted'}>{tAuth('signedInAs', { username })}</Text>
            <Button
              variant="ghost"
              color={'brand'}
              aria-label={tAuth('logout')}
              onClick={() => logout().catch(() => undefined)}
            >
              {tAuth('logout')}
            </Button>
          </>
        )}
        <Menu>
          <MenuButton
            as={Button}
            variant="ghost"
            color={'brand'}
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
