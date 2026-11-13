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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialCipherTest {

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  @Test
  void roundTripsPassword() {
    CredentialCipher cipher = new CredentialCipher(randomKey());
    String encrypted = cipher.encrypt("s3cret");
    assertThat(encrypted).startsWith(CredentialCipher.PREFIX).isNotEqualTo("s3cret");
    assertThat(cipher.decrypt(encrypted)).isEqualTo("s3cret");
  }

  @Test
  void usesFreshIvPerEncryption() {
    CredentialCipher cipher = new CredentialCipher(randomKey());
    assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
  }

  @Test
  void failsWithWrongKey() {
    String encrypted = new CredentialCipher(randomKey()).encrypt("s3cret");
    CredentialCipher other = new CredentialCipher(randomKey());
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> other.decrypt(encrypted));
  }

  @Test
  void passThroughWhenNoKeyConfigured() {
    CredentialCipher cipher = new CredentialCipher(null);
    assertThat(cipher.isEnabled()).isFalse();
    assertThat(cipher.encrypt("plain")).isEqualTo("plain");
    assertThat(cipher.decrypt("plain")).isEqualTo("plain");
  }

  @Test
  void decryptsLegacyPlaintextEvenWithKeyConfigured() {
    CredentialCipher cipher = new CredentialCipher(randomKey());
    assertThat(cipher.decrypt("legacy-plaintext")).isEqualTo("legacy-plaintext");
  }

  @Test
  void truncatedPayloadGivesClearError() {
    CredentialCipher cipher = new CredentialCipher(randomKey());
    String truncated = CredentialCipher.PREFIX + Base64.getEncoder().encodeToString(new byte[5]);
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> cipher.decrypt(truncated))
        .withMessageContaining("corrupt");
  }

  @Test
  void rejectsKeyOfWrongLength() {
    String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> new CredentialCipher(shortKey));
  }

  @Test
  void refusesToDecryptStoredCiphertextWithoutKey() {
    String encrypted = new CredentialCipher(randomKey()).encrypt("s3cret");
    CredentialCipher noKey = new CredentialCipher("");
    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> noKey.decrypt(encrypted));
  }
}
