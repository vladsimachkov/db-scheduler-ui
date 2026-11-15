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
import { useQuery } from '@tanstack/react-query';
import { SCHEMAS_QUERY_KEY, listSchemas } from 'src/services/schemas';
import { getUsersEnabled } from 'src/utils/config';

/**
 * The schemas the signed-in user may see (all of them for an admin). Inert while the users
 * feature is off.
 */
export function useSchemas() {
  return useQuery({
    queryKey: [SCHEMAS_QUERY_KEY],
    queryFn: listSchemas,
    enabled: getUsersEnabled(),
    staleTime: 60_000,
  });
}
