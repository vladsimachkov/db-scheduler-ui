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
package no.bekk.dbscheduler.ui.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create payload for a user; {@code enabled} defaults to true when omitted. The username charset is
 * restricted because usernames travel as URL path variables (schema grants) — a {@code /} or {@code
 * ?} in a name would make the user unaddressable by the grant API.
 */
public record UserCreateRequest(
    @NotBlank
        @Size(max = 100)
        @Pattern(
            regexp = "[A-Za-z0-9._@+-]+",
            message = "username may only contain letters, digits and . _ @ + -")
        String username,
    @NotBlank @Size(max = 100) String password,
    Boolean admin,
    Boolean enabled) {}
