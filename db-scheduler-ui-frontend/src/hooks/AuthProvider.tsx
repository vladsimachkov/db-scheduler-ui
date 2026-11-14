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
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, type ReactNode } from 'react';
import React from 'react';
import { AuthContext, type AuthContextValue } from 'src/hooks/authContext';
import { UNAUTHORIZED_EVENT } from 'src/services/apiClient';
import {
  ANONYMOUS,
  AUTH_ME_QUERY_KEY,
  AuthUser,
  getMe,
  login as loginService,
  logout as logoutService,
} from 'src/services/auth';
import { getUsersEnabled } from 'src/utils/config';

export const AuthProvider: React.FC<{ children: ReactNode }> = ({
  children,
}) => {
  const usersEnabled = getUsersEnabled();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: [AUTH_ME_QUERY_KEY],
    queryFn: getMe,
    enabled: usersEnabled,
    // the session does not change on its own; refreshes happen on login/logout and on 401
    refetchInterval: false,
    refetchOnWindowFocus: false,
    retry: false,
    staleTime: Infinity,
  });

  useEffect(() => {
    if (!usersEnabled) {
      return;
    }
    const onUnauthorized = () =>
      queryClient.setQueryData<AuthUser>([AUTH_ME_QUERY_KEY], ANONYMOUS);
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized);
  }, [queryClient, usersEnabled]);

  const value = useMemo<AuthContextValue>(
    () => ({
      usersEnabled,
      loading: usersEnabled && isLoading,
      authenticated: data?.authenticated ?? false,
      username: data?.username ?? null,
      admin: data?.admin ?? false,
      login: async (username: string, password: string) => {
        const user = await loginService(username, password);
        queryClient.setQueryData<AuthUser>([AUTH_ME_QUERY_KEY], user);
      },
      logout: async () => {
        await logoutService();
        queryClient.setQueryData<AuthUser>([AUTH_ME_QUERY_KEY], ANONYMOUS);
      },
    }),
    [usersEnabled, isLoading, data, queryClient],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

