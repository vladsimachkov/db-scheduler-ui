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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

  // Low iteration count: these tests only check semantics, not wall-clock hardness.
  private final PasswordHasher hasher = new PasswordHasher(1000);

  @Test
  void storesSelfDescribingFormat() {
    String stored = hasher.hash("hunter2");
    String[] parts = stored.split(":");
    assertThat(parts).hasSize(4);
    assertThat(parts[0]).isEqualTo("pbkdf2");
    assertThat(parts[1]).isEqualTo("1000");
    assertThat(parts[2]).isNotBlank();
    assertThat(parts[3]).isNotBlank();
    assertThat(stored).doesNotContain("hunter2");
  }

  @Test
  void defaultConstructorUsesDefaultIterations() {
    String stored = new PasswordHasher().hash("hunter2");
    assertThat(stored.split(":")[1]).isEqualTo(String.valueOf(PasswordHasher.DEFAULT_ITERATIONS));
  }

  @Test
  void matchesCorrectPassword() {
    assertThat(hasher.matches("hunter2", hasher.hash("hunter2"))).isTrue();
  }

  @Test
  void rejectsWrongPassword() {
    assertThat(hasher.matches("wrong", hasher.hash("hunter2"))).isFalse();
  }

  @Test
  void rejectsTamperedHash() {
    String stored = hasher.hash("hunter2");
    String[] parts = stored.split(":");
    // flip the last character of the derived-key segment
    char last = parts[3].charAt(parts[3].length() - 1);
    parts[3] = parts[3].substring(0, parts[3].length() - 1) + (last == 'A' ? 'B' : 'A');
    assertThat(hasher.matches("hunter2", String.join(":", parts))).isFalse();
  }

  @Test
  void freshSaltPerHash() {
    String first = hasher.hash("same");
    String second = hasher.hash("same");
    assertThat(first).isNotEqualTo(second);
    assertThat(hasher.matches("same", first)).isTrue();
    assertThat(hasher.matches("same", second)).isTrue();
  }

  @Test
  void hashFromOtherInstanceWithSameIterationsMatches() {
    assertThat(new PasswordHasher(1000).matches("hunter2", hasher.hash("hunter2"))).isTrue();
  }

  @Test
  void matchesSurviveIterationBump() {
    String old = new PasswordHasher(500).hash("hunter2");
    // a hasher configured with a higher default still verifies the old, lower-iteration hash
    assertThat(new PasswordHasher(100_000).matches("hunter2", old)).isTrue();
  }

  @Test
  void emptyPasswordRoundTrips() {
    assertThat(hasher.matches("", hasher.hash(""))).isTrue();
    assertThat(hasher.matches("x", hasher.hash(""))).isFalse();
  }

  @Test
  void malformedStoredValuesNeverMatchAndNeverThrow() {
    String good = hasher.hash("hunter2");
    assertThat(hasher.matches("hunter2", null)).isFalse();
    assertThat(hasher.matches(null, good)).isFalse();
    assertThat(hasher.matches("hunter2", "")).isFalse();
    assertThat(hasher.matches("hunter2", "garbage")).isFalse();
    assertThat(hasher.matches("hunter2", "bcrypt:10:salt:hash")).isFalse();
    assertThat(hasher.matches("hunter2", "pbkdf2:notanumber:c2FsdA==:aGFzaA==")).isFalse();
    assertThat(hasher.matches("hunter2", "pbkdf2:1000:!!!not-base64!!!:aGFzaA==")).isFalse();
    assertThat(hasher.matches("hunter2", "pbkdf2:1000:c2FsdA==")).isFalse();
    assertThat(hasher.matches("hunter2", "pbkdf2:0:c2FsdA==:aGFzaA==")).isFalse();
  }

  @Test
  void rejectsNonPositiveIterations() {
    assertThatThrownBy(() -> new PasswordHasher(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
