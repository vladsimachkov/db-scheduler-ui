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
  Alert,
  AlertDescription,
  Box,
  Button,
  Center,
  FormControl,
  FormLabel,
  Heading,
  Input,
  Stack,
} from '@chakra-ui/react';
import React, { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from 'src/hooks/useAuth';

/**
 * Session login for the built-in users. Rendered by FrontPage in place of every route while the
 * users feature is on and nobody is signed in — the API itself is guarded server-side; this screen
 * is only the door.
 */
export const LoginPage: React.FC = () => {
  const { t } = useTranslation('auth');
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await login(username, password);
    } catch (err) {
      setError(err instanceof Error && err.message ? err.message : t('error.generic'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Center minH="70vh">
      <Box
        as="form"
        onSubmit={onSubmit}
        w="sm"
        p={8}
        borderWidth={1}
        borderRadius="md"
        borderColor="border"
        bg="surface"
      >
        <Stack spacing={4}>
          <Heading size="md">{t('title')}</Heading>
          {error && (
            <Alert status="error" role="alert">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <FormControl isRequired>
            <FormLabel htmlFor="login-username">{t('username')}</FormLabel>
            <Input
              id="login-username"
              value={username}
              autoComplete="username"
              onChange={(e) => setUsername(e.target.value)}
            />
          </FormControl>
          <FormControl isRequired>
            <FormLabel htmlFor="login-password">{t('password')}</FormLabel>
            <Input
              id="login-password"
              type="password"
              value={password}
              autoComplete="current-password"
              onChange={(e) => setPassword(e.target.value)}
            />
          </FormControl>
          <Button type="submit" colorScheme="blue" isLoading={submitting}>
            {t('submit')}
          </Button>
        </Stack>
      </Box>
    </Center>
  );
};

export default LoginPage;
