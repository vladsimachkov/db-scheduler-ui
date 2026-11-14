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
package no.bekk.dbscheduler.ui.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfigResponse {
  private boolean showHistory;
  private boolean showOverview;
  private boolean showSql;
  private boolean readOnly;
  private boolean usersEnabled;

  public ConfigResponse() {}

  @JsonCreator
  public ConfigResponse(
      @JsonProperty("showHistory") boolean showHistory,
      @JsonProperty("showOverview") boolean showOverview,
      @JsonProperty("showSql") boolean showSql,
      @JsonProperty("readOnly") boolean readOnly,
      @JsonProperty("usersEnabled") boolean usersEnabled) {
    this.showHistory = showHistory;
    this.showOverview = showOverview;
    this.showSql = showSql;
    this.readOnly = readOnly;
    this.usersEnabled = usersEnabled;
  }

  /** Kept for hosts built against the pre-users response shape. */
  public ConfigResponse(
      boolean showHistory, boolean showOverview, boolean showSql, boolean readOnly) {
    this(showHistory, showOverview, showSql, readOnly, false);
  }
}
