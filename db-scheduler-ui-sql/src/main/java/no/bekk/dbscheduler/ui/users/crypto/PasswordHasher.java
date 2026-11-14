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
package no.bekk.dbscheduler.ui.users.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2-SHA256 password hashing for UI users, built on JDK primitives only — the users feature
 * deliberately carries no Spring Security (or other crypto-library) dependency.
 *
 * <p>Stored format: {@code pbkdf2:<iterations>:<salt-base64>:<hash-base64>}. The iteration count
 * travels with each hash so existing hashes keep verifying after the default is raised.
 */
public final class PasswordHasher {

  public static final int DEFAULT_ITERATIONS = 210_000;

  private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
  private static final String PREFIX = "pbkdf2";
  private static final int SALT_BYTES = 16;
  private static final int KEY_BITS = 256;

  private final int iterations;
  private final SecureRandom random = new SecureRandom();

  public PasswordHasher() {
    this(DEFAULT_ITERATIONS);
  }

  /**
   * @param iterations PBKDF2 rounds; lower values exist only to keep tests fast.
   */
  public PasswordHasher(int iterations) {
    if (iterations < 1) {
      throw new IllegalArgumentException("iterations must be positive: " + iterations);
    }
    this.iterations = iterations;
  }

  /** Hashes a plaintext password into the self-describing stored format. */
  public String hash(String password) {
    byte[] salt = new byte[SALT_BYTES];
    random.nextBytes(salt);
    byte[] hash = pbkdf2(password.toCharArray(), salt, iterations);
    return PREFIX
        + ":"
        + iterations
        + ":"
        + Base64.getEncoder().encodeToString(salt)
        + ":"
        + Base64.getEncoder().encodeToString(hash);
  }

  /**
   * Constant-time check of a plaintext password against a stored hash. Malformed stored values
   * (wrong prefix, bad base64, non-numeric iterations) never throw — they simply never match.
   */
  public boolean matches(String password, String stored) {
    if (password == null || stored == null) {
      return false;
    }
    // Base64 never contains ':' — a plain split is unambiguous.
    String[] parts = stored.split(":");
    if (parts.length != 4 || !PREFIX.equals(parts[0])) {
      return false;
    }
    int storedIterations;
    byte[] salt;
    byte[] expected;
    try {
      storedIterations = Integer.parseInt(parts[1]);
      salt = Base64.getDecoder().decode(parts[2]);
      expected = Base64.getDecoder().decode(parts[3]);
    } catch (RuntimeException malformed) {
      return false;
    }
    if (storedIterations < 1 || salt.length == 0 || expected.length == 0) {
      return false;
    }
    byte[] actual = pbkdf2(password.toCharArray(), salt, storedIterations);
    return MessageDigest.isEqual(expected, actual);
  }

  private static byte[] pbkdf2(char[] password, byte[] salt, int iterations) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
      throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable on this JVM", e);
    }
  }
}
