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
package no.bekk.dbscheduler.ui.util.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import no.bekk.dbscheduler.ui.model.TaskDisplay;
import no.bekk.dbscheduler.ui.model.TaskModel;
import org.junit.jupiter.api.Test;

class TaskMapperDisplayTest {

  private static TaskModel ungrouped(String instance, TaskDisplay display) {
    TaskModel model = new TaskModel();
    model.setTaskName("sql-script-task");
    model.setTaskInstance(Collections.singletonList(instance));
    model.setTaskData(Collections.singletonList("payload"));
    model.setExecutionTime(Collections.singletonList(Instant.now()));
    model.setPicked(Collections.singletonList(false));
    model.setPickedBy(Collections.singletonList(""));
    model.setLastSuccess(List.of());
    model.setConsecutiveFailures(Collections.singletonList(0));
    if (display != null) {
      model.setDisplays(Collections.singletonList(display));
    }
    return model;
  }

  @Test
  void groupTasks_mergesDisplaysInSortedOrder() {
    TaskDisplay first = new TaskDisplay("nightly", "s1");
    TaskDisplay second = new TaskDisplay("hourly", "s2");
    TaskModel a = ungrouped("j1", first);
    TaskModel b = ungrouped("j2", second);

    List<TaskModel> grouped = TaskMapper.groupTasks(List.of(b, a));

    assertThat(grouped).hasSize(1);
    assertThat(grouped.get(0).getTaskInstance()).containsExactly("j1", "j2");
    assertThat(grouped.get(0).getDisplays()).containsExactly(first, second);
  }

  @Test
  void groupTasks_memberWithoutDisplaysContributesNullHole() {
    TaskDisplay display = new TaskDisplay("nightly", "s1");
    TaskModel withDisplay = ungrouped("j1", display);
    TaskModel withoutDisplay = ungrouped("j2", null);

    List<TaskModel> grouped = TaskMapper.groupTasks(List.of(withDisplay, withoutDisplay));

    assertThat(grouped.get(0).getDisplays()).containsExactly(display, null);
  }

  @Test
  void groupTasks_allMembersWithoutDisplays_staysNull() {
    List<TaskModel> grouped =
        TaskMapper.groupTasks(List.of(ungrouped("j1", null), ungrouped("j2", null)));

    assertThat(grouped.get(0).getDisplays()).isNull();
  }
}
