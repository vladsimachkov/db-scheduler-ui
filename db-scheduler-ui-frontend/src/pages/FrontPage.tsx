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
import { Box } from '@chakra-ui/react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { TopBar } from 'src/components/common/TopBar';
import { LogList } from 'src/components/history/LogList';
import { useAuth } from 'src/hooks/useAuth';
import AdminSchemasPage from 'src/pages/AdminSchemasPage';
import AdminUsersPage from 'src/pages/AdminUsersPage';
import { LoginPage } from 'src/pages/LoginPage';
import TaskList from 'src/components/scheduled/TaskList';
import { OverviewPage } from 'src/pages/OverviewPage';
import SqlConnectionsPage from 'src/pages/SqlConnectionsPage';
import SqlJobExecutionsPage from 'src/pages/SqlJobExecutionsPage';
import SqlJobsPage from 'src/pages/SqlJobsPage';
import {
  getReadonly,
  getShowHistory,
  getShowOverview,
  getShowSql,
} from 'src/utils/config';

export const FrontPage: React.FC = () => {
  const showHistory = getShowHistory();
  const showOverview = getShowOverview();
  const showSql = getShowSql();
  const { usersEnabled, authenticated, loading, admin } = useAuth();

  // The API is guarded server-side; this only decides what the SPA paints. While the first
  // /auth/me is in flight nothing is rendered rather than flashing the task list.
  if (usersEnabled && loading) {
    return null;
  }
  if (usersEnabled && !authenticated) {
    return <LoginPage />;
  }
  return (
    <>
      <TopBar title={'DB Scheduler UI'} />
      <Box mx={20} mt={14}>
        <Routes>
          <Route
            index
            element={showOverview ? <Navigate to="/overview" replace /> : <TaskList />}
          ></Route>
          {showOverview && (
            <>
              <Route path="/overview" element={<OverviewPage />}></Route>
              <Route path="/scheduled" element={<TaskList />}></Route>
              <Route path="/scheduled/:taskName" element={<TaskList />}></Route>
              <Route
                path="/scheduled/:taskName/page/:page"
                element={<TaskList />}
              ></Route>
            </>
          )}
          {showSql && (
            <>
              <Route path="/sql-jobs" element={<SqlJobsPage />}></Route>
              <Route
                path="/sql-jobs/:jobId/executions"
                element={<SqlJobExecutionsPage />}
              ></Route>
              <Route
                path="/sql-connections"
                element={<SqlConnectionsPage />}
              ></Route>
            </>
          )}
          {usersEnabled && showSql && admin && !getReadonly() && (
            <>
              <Route path="/admin" element={<Navigate to="/admin/users" replace />}></Route>
              <Route path="/admin/users" element={<AdminUsersPage />}></Route>
              <Route path="/admin/schemas" element={<AdminSchemasPage />}></Route>
            </>
          )}
          {usersEnabled && showSql && (!admin || getReadonly()) && (
            // the API enforces admin-only anyway; this just keeps the SPA from painting
            // an empty shell for a link someone pasted
            <Route path="/admin/*" element={<Navigate to="/" replace />}></Route>
          )}
          <Route path="/:taskName" element={<TaskList />}></Route>
          <Route path="/:taskName/page/:page" element={<TaskList />}></Route>
          <Route
            path="/history/:taskName/:taskInstance"
            element={<LogList />}
          ></Route>
          {showHistory && (
            <Route path="/history/all" element={<LogList />}></Route>
          )}
        </Routes>
      </Box>
    </>
  );
};
