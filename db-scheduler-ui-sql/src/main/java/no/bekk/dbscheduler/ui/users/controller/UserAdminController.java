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
package no.bekk.dbscheduler.ui.users.controller;

import jakarta.validation.Valid;
import java.util.List;
import no.bekk.dbscheduler.ui.users.dto.PasswordResetRequest;
import no.bekk.dbscheduler.ui.users.dto.UserCreateRequest;
import no.bekk.dbscheduler.ui.users.dto.UserDto;
import no.bekk.dbscheduler.ui.users.dto.UserUpdateRequest;
import no.bekk.dbscheduler.ui.users.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User administration; every call is admin-enforced in {@link UserService} and the controller is
 * absent when {@code db-scheduler-ui.read-only=true}. Password hashes never leave the service.
 */
@RestController
@RequestMapping("/db-scheduler-api/users")
public class UserAdminController {

  private final UserService users;

  public UserAdminController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public List<UserDto> list() {
    return users.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserDto create(@Valid @RequestBody UserCreateRequest request) {
    return users.create(request);
  }

  @PutMapping("/{id}")
  public UserDto update(@PathVariable String id, @RequestBody UserUpdateRequest request) {
    return users.update(id, request);
  }

  @PostMapping("/{id}/password")
  public void resetPassword(
      @PathVariable String id, @Valid @RequestBody PasswordResetRequest request) {
    users.resetPassword(id, request);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id) {
    users.delete(id);
  }
}
