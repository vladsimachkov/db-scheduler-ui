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
package no.bekk.dbscheduler.ui.sql.exec;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Snowflake-style id generator for {@code sql_job_executions}: {@code timestamp(ms) << 20 | node <<
 * 10 | sequence}. The sequence is per-millisecond (0..1023, waiting for the next millisecond on
 * overflow), so ids are unique within the JVM and time-ordered, which keeps the primary-key index
 * append-only.
 *
 * <p>Cross-JVM uniqueness relies on the 10-bit node id: by default a random value, which has a
 * birthday-paradox collision chance in large fleets. For production clusters, give each node a
 * distinct id via {@code -Ddb-scheduler-ui.sql.node-id=0..1023}.
 */
public final class IdGenerator {

  private static final int SEQUENCE_BITS = 10;
  private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
  private static final long NODE = resolveNode();

  private static long lastTimestamp = -1L;
  private static long sequence = 0L;

  private IdGenerator() {}

  private static long resolveNode() {
    String configured = System.getProperty("db-scheduler-ui.sql.node-id");
    if (configured == null || configured.isBlank()) {
      return ThreadLocalRandom.current().nextLong(MAX_SEQUENCE + 1);
    }
    long node;
    try {
      node = Long.parseLong(configured.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
          "db-scheduler-ui.sql.node-id must be a number between 0 and " + MAX_SEQUENCE, e);
    }
    if (node < 0 || node > MAX_SEQUENCE) {
      throw new IllegalArgumentException(
          "db-scheduler-ui.sql.node-id must be between 0 and " + MAX_SEQUENCE);
    }
    return node;
  }

  public static synchronized long next() {
    long timestamp = System.currentTimeMillis();
    if (timestamp == lastTimestamp) {
      sequence = (sequence + 1) & MAX_SEQUENCE;
      if (sequence == 0) {
        // sequence exhausted for this millisecond: wait for the next one
        while (timestamp <= lastTimestamp) {
          timestamp = System.currentTimeMillis();
        }
      }
    } else {
      sequence = 0;
    }
    lastTimestamp = timestamp;
    return (timestamp << (2 * SEQUENCE_BITS)) | (NODE << SEQUENCE_BITS) | sequence;
  }
}
