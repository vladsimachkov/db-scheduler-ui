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
package no.bekk.dbscheduler.ui.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.kagkarlsson.scheduler.ScheduledExecution;
import com.github.kagkarlsson.scheduler.ScheduledExecutionsFilter;
import com.github.kagkarlsson.scheduler.Scheduler;
import com.github.kagkarlsson.scheduler.task.Execution;
import com.github.kagkarlsson.scheduler.task.TaskInstance;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import no.bekk.dbscheduler.ui.model.PollResponse;
import no.bekk.dbscheduler.ui.model.TaskDetailsRequestParams;
import no.bekk.dbscheduler.ui.util.Caching;
import org.junit.jupiter.api.Test;

class TaskLogicPollTest {

  private static ScheduledExecution<Object> execution(
      String taskName, String instanceId, boolean failing, boolean picked) {
    TaskInstance<Object> taskInstance = new TaskInstance<>(taskName, instanceId);
    Execution execution =
        new Execution(
            Instant.now(),
            taskInstance,
            picked,
            picked ? "worker-1" : null,
            null,
            failing ? Instant.now() : null,
            failing ? 1 : 0,
            null,
            0);
    return new ScheduledExecution<>(Object.class, execution);
  }

  @Test
  void pollTasks_countsStoppedFailingAndFinishedRunning() {
    Scheduler scheduler = mock(Scheduler.class);
    Caching caching = new Caching();
    TaskLogic taskLogic = new TaskLogic(scheduler, caching, true);

    // Cached (previous) state: t1 was failing, t2 was running
    caching.updateCache(
        List.of(execution("t1", "i1", true, false), execution("t2", "i2", false, true)));

    // Current DB state: both tasks are clean now
    when(scheduler.getScheduledExecutions())
        .thenReturn(
            new ArrayList<>(
                List.of(execution("t1", "i1", false, false), execution("t2", "i2", false, false))));
    when(scheduler.getScheduledExecutions(any(ScheduledExecutionsFilter.class)))
        .thenReturn(new ArrayList<>());

    PollResponse response = taskLogic.pollTasks(TaskDetailsRequestParams.builder().build());

    assertThat(response.getStoppedFailing()).isEqualTo(1);
    assertThat(response.getFinishedRunning()).isEqualTo(1);
  }

  @Test
  void pollTasks_noStatusChange_countsStayZero() {
    Scheduler scheduler = mock(Scheduler.class);
    Caching caching = new Caching();
    TaskLogic taskLogic = new TaskLogic(scheduler, caching, true);

    ScheduledExecution<Object> failing = execution("t1", "i1", true, false);
    caching.updateCache(List.of(failing));

    when(scheduler.getScheduledExecutions())
        .thenReturn(new ArrayList<>(List.of(execution("t1", "i1", true, false))));
    when(scheduler.getScheduledExecutions(any(ScheduledExecutionsFilter.class)))
        .thenReturn(new ArrayList<>());

    PollResponse response = taskLogic.pollTasks(TaskDetailsRequestParams.builder().build());

    assertThat(response.getStoppedFailing()).isZero();
    assertThat(response.getFinishedRunning()).isZero();
  }
}
