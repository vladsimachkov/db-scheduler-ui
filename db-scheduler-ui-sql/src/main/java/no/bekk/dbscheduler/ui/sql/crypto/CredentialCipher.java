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
package no.bekk.dbscheduler.ui.sql.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES/GCM encryption for connection passwords, versioned with an {@code enc:v1:} prefix.
 *
 * <p>When no key is configured the cipher degrades to pass-through (values stored as-is) — the
 * auto-configuration logs a loud warning at startup in that case.
 */
public class CredentialCipher {

  public static final String PREFIX = "enc:v1:";
  private static final int GCM_IV_LENGTH_BYTES = 12;
  private static final int GCM_TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  /**
   * @param base64Key 256-bit key, base64-encoded; {@code null}/blank for pass-through mode
   */
  public CredentialCipher(String base64Key) {
    if (base64Key == null || base64Key.isBlank()) {
      this.key = null;
      return;
    }
    byte[] decoded = Base64.getDecoder().decode(base64Key);
    if (decoded.length != 32) {
      throw new IllegalArgumentException(
          "db-scheduler-ui.sql.encryption-key must be a base64-encoded 256-bit (32 byte) key");
    }
    this.key = new SecretKeySpec(decoded, "AES");
  }

  public boolean isEnabled() {
    return key != null;
  }

  public String encrypt(String plaintext) {
    if (key == null || plaintext == null) {
      return plaintext;
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload = new byte[iv.length + ciphertext.length];
      System.arraycopy(iv, 0, payload, 0, iv.length);
      System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
      return PREFIX + Base64.getEncoder().encodeToString(payload);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt credential", e);
    }
  }

  public String decrypt(String stored) {
    if (stored == null || !stored.startsWith(PREFIX)) {
      return stored;
    }
    if (key == null) {
      throw new IllegalStateException(
          "Stored credentials are encrypted but db-scheduler-ui.sql.encryption-key is not configured");
    }
    try {
      byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
      if (payload.length <= GCM_IV_LENGTH_BYTES) {
        throw new IllegalStateException(
            "Stored credential is corrupt (payload shorter than an IV)");
      }
      byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
      System.arraycopy(payload, 0, iv, 0, iv.length);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
      byte[] plaintext = cipher.doFinal(payload, iv.length, payload.length - iv.length);
      return new String(plaintext, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Failed to decrypt credential (wrong key?)", e);
    }
  }
}
