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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import no.bekk.dbscheduler.ui.model.GetLogsResponse;
import no.bekk.dbscheduler.ui.model.LogModel;
import no.bekk.dbscheduler.ui.model.LogPollResponse;
import no.bekk.dbscheduler.ui.model.TaskDetailsRequestParams;
import no.bekk.dbscheduler.ui.model.TaskRequestParams.TaskFilter;
import no.bekk.dbscheduler.ui.testsupport.LogsRow;
import no.bekk.dbscheduler.ui.testsupport.LogsTable;
import no.bekk.dbscheduler.ui.testsupport.TestDatabase;
import no.bekk.dbscheduler.ui.util.Caching;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LogLogicTest {

  private DataSource dataSource;
  private Caching caching;
  private LogLogic logLogic;
  private LogsTable logsTable;

  @BeforeEach
  void setUp() {
    dataSource = TestDatabase.newDataSourceWithTables();
    caching = new Caching();
    logLogic = new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 0);
    logsTable = new LogsTable(dataSource);
  }

  // --- Retrieval ---

  @Test
  void getLogsDirectlyFromDB_singleEntry_returnsInsertedLog() {
    logsTable.insert(LogsRow.defaultRow().build());

    List<LogModel> logs = logLogic.getLogsDirectlyFromDB(defaultRequest());

    assertThat(logs)
        .singleElement()
        .satisfies(
            log -> {
              assertThat(log.getTaskName()).isEqualTo("happy-task");
              assertThat(log.getTaskInstance()).isEqualTo("happy-task-1");
              assertThat(log.isSucceeded()).isTrue();
            });
  }

  @Test
  void getLogsDirectlyFromDB_emptyDatabase_returnsEmpty() {
    assertThat(logLogic.getLogsDirectlyFromDB(defaultRequest())).isEmpty();
  }

  @Test
  void getLogsDirectlyFromDB_withLogLimit_capsReturnedRows() {
    for (int i = 0; i < 5; i++) {
      logsTable.insert(LogsRow.defaultRow().build());
    }
    LogLogic limited = new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 2);

    List<LogModel> logs = limited.getLogsDirectlyFromDB(defaultRequest());

    assertThat(logs).hasSize(2);
  }

  // --- Filter ---

  @Test
  void getLogsDirectlyFromDB_failedFilter_returnsOnlyFailedLogs() {
    logsTable.insert(LogsRow.defaultRow().taskName("ok-job").succeeded(true).build());
    logsTable.insert(LogsRow.defaultRow().taskName("failing-job").succeeded(false).build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder().filter(TaskFilter.FAILED).build());

    assertThat(logs).extracting(LogModel::getTaskName).containsExactly("failing-job");
  }

  @Test
  void getLogsDirectlyFromDB_succeededFilter_returnsOnlySucceededLogs() {
    logsTable.insert(LogsRow.defaultRow().taskName("ok-job").succeeded(true).build());
    logsTable.insert(LogsRow.defaultRow().taskName("failing-job").succeeded(false).build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder().filter(TaskFilter.SUCCEEDED).build());

    assertThat(logs).extracting(LogModel::getTaskName).containsExactly("ok-job");
  }

  @Test
  void getLogsDirectlyFromDB_allFilter_returnsBothSucceededAndFailedLogs() {
    logsTable.insert(LogsRow.defaultRow().taskName("ok-job").succeeded(true).build());
    logsTable.insert(LogsRow.defaultRow().taskName("failing-job").succeeded(false).build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder().filter(TaskFilter.ALL).build());

    assertThat(logs)
        .extracting(LogModel::getTaskName)
        .containsExactlyInAnyOrder("ok-job", "failing-job");
  }

  // --- Time range ---

  @Test
  void getLogsDirectlyFromDB_startTimeFilter_returnsLogsAtOrAfterTime() {
    Instant now = Instant.now();
    logsTable.insert(
        LogsRow.defaultRow().taskName("old").time(now.minus(Duration.ofHours(2))).build());
    logsTable.insert(LogsRow.defaultRow().taskName("new").time(now).build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder().startTime(now.minus(Duration.ofHours(1))).build());

    assertThat(logs).extracting(LogModel::getTaskName).containsExactly("new");
  }

  @Test
  void getLogsDirectlyFromDB_endTimeFilter_returnsLogsAtOrBeforeTime() {
    Instant now = Instant.now();
    logsTable.insert(
        LogsRow.defaultRow().taskName("old").time(now.minus(Duration.ofHours(2))).build());
    logsTable.insert(LogsRow.defaultRow().taskName("new").time(now).build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder().endTime(now.minus(Duration.ofHours(1))).build());

    assertThat(logs).extracting(LogModel::getTaskName).containsExactly("old");
  }

  @Test
  void getLogsDirectlyFromDB_startAndEndTimeRange_returnsLogsInsideRange() {
    Instant now = Instant.now();
    logsTable.insert(
        LogsRow.defaultRow().taskName("old").time(now.minus(Duration.ofHours(3))).build());
    logsTable.insert(
        LogsRow.defaultRow().taskName("inside").time(now.minus(Duration.ofMinutes(45))).build());
    logsTable.insert(LogsRow.defaultRow().taskName("new").time(now).build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder()
                .startTime(now.minus(Duration.ofHours(1)))
                .endTime(now.minus(Duration.ofMinutes(30)))
                .build());

    assertThat(logs).extracting(LogModel::getTaskName).containsExactly("inside");
  }

  // --- Search ---

  @Test
  void getLogsDirectlyFromDB_searchByTaskName_returnsPartialMatches() {
    logsTable.insert(LogsRow.defaultRow().taskName("send-email").build());
    logsTable.insert(LogsRow.defaultRow().taskName("send-sms").build());
    logsTable.insert(LogsRow.defaultRow().taskName("cleanup").build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder().searchTermTaskName("send").build());

    assertThat(logs)
        .extracting(LogModel::getTaskName)
        .containsExactlyInAnyOrder("send-email", "send-sms");
  }

  @Test
  void getLogsDirectlyFromDB_searchByTaskNameExactMatch_returnsOnlyExactMatch() {
    logsTable.insert(LogsRow.defaultRow().taskName("send-email").build());
    logsTable.insert(LogsRow.defaultRow().taskName("send-sms").build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder()
                .searchTermTaskName("send-email")
                .taskNameExactMatch(true)
                .build());

    assertThat(logs).extracting(LogModel::getTaskName).containsExactly("send-email");
  }

  @Test
  void getLogsDirectlyFromDB_searchByTaskInstance_returnsPartialMatches() {
    logsTable.insert(LogsRow.defaultRow().taskInstance("user-42-payment").build());
    logsTable.insert(LogsRow.defaultRow().taskInstance("user-99-payment").build());
    logsTable.insert(LogsRow.defaultRow().taskInstance("system-cleanup").build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder().searchTermTaskInstance("user").build());

    assertThat(logs)
        .extracting(LogModel::getTaskInstance)
        .containsExactlyInAnyOrder("user-42-payment", "user-99-payment");
  }

  @Test
  void getLogsDirectlyFromDB_searchByTaskInstanceExactMatch_returnsOnlyExactMatch() {
    logsTable.insert(LogsRow.defaultRow().taskInstance("user-42-payment").build());
    logsTable.insert(LogsRow.defaultRow().taskInstance("user-99-payment").build());

    List<LogModel> logs =
        logLogic.getLogsDirectlyFromDB(
            TaskDetailsRequestParams.builder()
                .searchTermTaskInstance("user-42-payment")
                .taskInstanceExactMatch(true)
                .build());

    assertThat(logs).extracting(LogModel::getTaskInstance).containsExactly("user-42-payment");
  }

  // --- Pagination ---

  @Test
  void getLogs_firstPage_returnsPageSliceAndTotalCount() {
    for (int i = 0; i < 5; i++) {
      logsTable.insert(LogsRow.defaultRow().build());
    }

    GetLogsResponse response = logLogic.getLogs(TaskDetailsRequestParams.builder().size(2).build());

    assertThat(response.getNumberOfItems()).isEqualTo(5);
    assertThat(response.getItems()).hasSize(2);
    assertThat(response.getNumberOfPages()).isEqualTo(3);
  }

  @Test
  void getLogs_lastPage_returnsRemainingItems() {
    for (int i = 0; i < 5; i++) {
      logsTable.insert(LogsRow.defaultRow().build());
    }

    GetLogsResponse response =
        logLogic.getLogs(TaskDetailsRequestParams.builder().size(2).pageNumber(2).build());

    assertThat(response.getItems()).hasSize(1);
  }

  @Test
  void getLogs_pageOutOfBounds_returnsEmptyItems() {
    for (int i = 0; i < 3; i++) {
      logsTable.insert(LogsRow.defaultRow().build());
    }

    GetLogsResponse response =
        logLogic.getLogs(TaskDetailsRequestParams.builder().size(2).pageNumber(5).build());

    assertThat(response.getItems()).isEmpty();
    assertThat(response.getNumberOfItems()).isEqualTo(3);
  }

  @Test
  void getLogs_emptyDatabase_returnsZeroItemsAndZeroPages() {
    GetLogsResponse response = logLogic.getLogs(defaultRequest());

    assertThat(response.getNumberOfItems()).isZero();
    assertThat(response.getItems()).isEmpty();
    assertThat(response.getNumberOfPages()).isZero();
  }

  // --- Polling ---

  @Test
  void pollLogs_emptyCache_countsAllLogsAsNew() {
    logsTable.insert(LogsRow.defaultRow().taskName("ok-job").succeeded(true).build());
    logsTable.insert(LogsRow.defaultRow().taskName("failing-job-1").succeeded(false).build());
    logsTable.insert(LogsRow.defaultRow().taskName("failing-job-2").succeeded(false).build());

    LogPollResponse response = logLogic.pollLogs(defaultRequest());

    assertThat(response.getNewSucceeded()).isEqualTo(1);
    assertThat(response.getNewFailures()).isEqualTo(2);
  }

  @Test
  void pollLogs_logsAlreadyInCache_reportsNoNewLogs() {
    logsTable.insert(LogsRow.defaultRow().succeeded(true).build());

    caching.updateLogCache(logLogic.getLogsDirectlyFromDB(defaultRequest()));

    LogPollResponse response = logLogic.pollLogs(defaultRequest());

    assertThat(response.getNewSucceeded()).isZero();
    assertThat(response.getNewFailures()).isZero();
  }

  @Test
  void pollLogs_mixedCachedAndNewLogs_countsOnlyNew() {
    logsTable.insert(LogsRow.defaultRow().taskName("cached").succeeded(true).build());

    caching.updateLogCache(logLogic.getLogsDirectlyFromDB(defaultRequest()));

    logsTable.insert(LogsRow.defaultRow().taskName("new-success").succeeded(true).build());
    logsTable.insert(LogsRow.defaultRow().taskName("new-failure").succeeded(false).build());

    LogPollResponse response = logLogic.pollLogs(defaultRequest());

    assertThat(response.getNewSucceeded()).isEqualTo(1);
    assertThat(response.getNewFailures()).isEqualTo(1);
  }

  // --- Visibility filter ---

  private static ExecutionVisibilityFilter hideSecret() {
    return new ExecutionVisibilityFilter() {
      @Override
      public boolean needsTaskData(String taskName) {
        return "guarded-task".equals(taskName);
      }

      @Override
      public boolean visible(
          String taskName, String taskInstance, Object taskData, String schemaId) {
        return !"secret-task".equals(taskName);
      }
    };
  }

  private static byte[] serialize(Object payload) {
    try (java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
      oos.writeObject(payload);
      oos.flush();
      return bos.toByteArray();
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Test
  void getLogs_visibilityFilter_hidesRowsAndCountsAfterFilter() {
    LogLogic guarded =
        new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 0, hideSecret());
    logsTable.insert(LogsRow.defaultRow().taskName("open-task").build());
    logsTable.insert(LogsRow.defaultRow().taskName("secret-task").build());

    GetLogsResponse response = guarded.getLogs(defaultRequest());

    assertThat(response.getNumberOfItems()).isEqualTo(1);
    assertThat(response.getItems()).extracting(LogModel::getTaskName).containsExactly("open-task");
  }

  @Test
  void getLogs_visibilityFilter_appliedToCachedRowsToo() {
    LogLogic guarded =
        new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 0, hideSecret());
    logsTable.insert(LogsRow.defaultRow().taskName("open-task").build());
    logsTable.insert(LogsRow.defaultRow().taskName("secret-task").build());
    // Prime the shared cache with the unfiltered rows, as another user's refresh would.
    caching.updateLogCache(guarded.getLogsDirectlyFromDB(defaultRequest()));

    GetLogsResponse cached =
        guarded.getLogs(TaskDetailsRequestParams.builder().refresh(false).build());

    assertThat(cached.getItems()).extracting(LogModel::getTaskName).containsExactly("open-task");
  }

  @Test
  void getLogs_visibilityFilter_forwardsSchemaId() {
    java.util.concurrent.atomic.AtomicReference<String> seen =
        new java.util.concurrent.atomic.AtomicReference<>();
    LogLogic guarded =
        new LogLogic(
            dataSource,
            null,
            caching,
            false,
            LogsTable.NAME,
            0,
            (taskName, taskInstance, taskData, schemaId) -> {
              seen.set(schemaId);
              return true;
            });
    logsTable.insert(LogsRow.defaultRow().build());

    guarded.getLogs(TaskDetailsRequestParams.builder().schemaId("schema-x").build());

    assertThat(seen.get()).isEqualTo("schema-x");
  }

  @Test
  void pollLogs_visibilityFilter_excludesHiddenFromCounts() {
    LogLogic guarded =
        new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 0, hideSecret());
    logsTable.insert(LogsRow.defaultRow().taskName("open-task").build());
    logsTable.insert(LogsRow.defaultRow().taskName("secret-task").build());

    LogPollResponse response = guarded.pollLogs(defaultRequest());

    assertThat(response.getNewSucceeded()).isEqualTo(1);
    assertThat(response.getNewFailures()).isZero();
  }

  @Test
  void getLogs_showDataFalse_stripsForcedTaskDataButKeepsItInCacheForFiltering() {
    // A filter that decides on the data itself — only possible if the cached rows keep it.
    ExecutionVisibilityFilter hideByData =
        new ExecutionVisibilityFilter() {
          @Override
          public boolean needsTaskData(String taskName) {
            return true;
          }

          @Override
          public boolean visible(
              String taskName, String taskInstance, Object taskData, String schemaId) {
            return !"secret".equals(taskData);
          }
        };
    LogLogic guarded =
        new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 0, hideByData);
    logsTable.insert(
        LogsRow.defaultRow().taskName("open-task").taskData(serialize("open")).build());
    logsTable.insert(
        LogsRow.defaultRow().taskName("secret-task").taskData(serialize("secret")).build());

    GetLogsResponse response = guarded.getLogs(defaultRequest());

    // hidden row filtered out, visible row returned without its data (showData=false contract)
    assertThat(response.getItems()).extracting(LogModel::getTaskName).containsExactly("open-task");
    assertThat(response.getItems()).allSatisfy(l -> assertThat(l.getTaskData()).isNull());

    // cached path: the shared cache must still hold the data for the filter to work again
    GetLogsResponse cached =
        guarded.getLogs(TaskDetailsRequestParams.builder().refresh(false).build());
    assertThat(cached.getItems()).extracting(LogModel::getTaskName).containsExactly("open-task");
    assertThat(cached.getItems()).allSatisfy(l -> assertThat(l.getTaskData()).isNull());
  }

  @Test
  void rowMapper_forcedDeserialization_populatesDataEvenWhenShowDataIsFalse() {
    LogLogic guarded =
        new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 0, hideSecret());
    logsTable.insert(
        LogsRow.defaultRow().taskName("guarded-task").taskData(serialize("payload")).build());
    logsTable.insert(
        LogsRow.defaultRow().taskName("unguarded-task").taskData(serialize("hidden")).build());

    List<LogModel> logs = guarded.getLogsDirectlyFromDB(defaultRequest());

    assertThat(logs)
        .filteredOn(l -> l.getTaskName().equals("guarded-task"))
        .singleElement()
        .satisfies(l -> assertThat(l.getTaskData()).isEqualTo("payload"));
    assertThat(logs)
        .filteredOn(l -> l.getTaskName().equals("unguarded-task"))
        .singleElement()
        .satisfies(l -> assertThat(l.getTaskData()).isNull());
  }

  @Test
  void rowMapper_corruptBlobOnForcedPath_keepsRowWithNullData() {
    LogLogic guarded =
        new LogLogic(dataSource, null, caching, false, LogsTable.NAME, 0, hideSecret());
    logsTable.insert(
        LogsRow.defaultRow().taskName("guarded-task").taskData(new byte[] {1, 2, 3}).build());

    List<LogModel> logs = guarded.getLogsDirectlyFromDB(defaultRequest());

    assertThat(logs).singleElement().satisfies(l -> assertThat(l.getTaskData()).isNull());
  }

  // --- Helper ---

  private static TaskDetailsRequestParams defaultRequest() {
    return TaskDetailsRequestParams.builder().build();
  }
}
