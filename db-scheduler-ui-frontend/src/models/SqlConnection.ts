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

export type SqlDialect =
  | 'ORACLE'
  | 'TRINO'
  | 'IMPALA'
  | 'MYSQL'
  | 'MARIADB'
  | 'POSTGRESQL'
  | 'MSSQL'
  | 'CLICKHOUSE'
  | 'GENERIC';

export interface SqlConnection {
  id: string;
  name: string;
  schemaId: string | null;
  dialect: SqlDialect;
  jdbcUrl: string;
  driverClass: string | null;
  username: string | null;
  passwordSet: boolean;
  propertiesJson: string | null;
  createdTime: string;
  updatedTime: string;
}

export interface SqlConnectionRequest {
  name: string;
  schemaId?: string;
  dialect: SqlDialect;
  jdbcUrl: string;
  driverClass?: string;
  username?: string;
  password?: string;
  clearPassword?: boolean;
  propertiesJson?: string;
}

export interface ConnectionTestResult {
  success: boolean;
  productName: string | null;
  productVersion: string | null;
  error: string | null;
}
