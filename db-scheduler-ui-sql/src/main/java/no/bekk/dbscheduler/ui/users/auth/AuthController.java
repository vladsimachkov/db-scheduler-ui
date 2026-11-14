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
package no.bekk.dbscheduler.ui.users.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import no.bekk.dbscheduler.ui.users.dto.AuthUserDto;
import no.bekk.dbscheduler.ui.users.dto.LoginRequest;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Session login endpoints for the built-in users. All three are reachable without a session (the
 * {@link AuthSessionFilter} public list); everything else under {@code /db-scheduler-api} needs
 * one. Deliberately no {@code @CrossOrigin}: the SPA is same-origin (the Vite dev server proxies
 * the API), and a wildcard CORS on the login endpoint would only widen the credential-stuffing
 * surface.
 */
@RestController
@RequestMapping("/db-scheduler-api/auth")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/login")
  public AuthUserDto login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
    AuthPrincipal principal =
        auth.authenticate(request.username(), request.password())
            // same message for unknown user, disabled account and wrong password
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
    // rotate the id the request arrived with before binding the identity to it
    http.changeSessionId();
    http.getSession().setAttribute(AuthPrincipal.SESSION_KEY, principal);
    return new AuthUserDto(true, principal.username(), principal.admin());
  }

  @PostMapping("/logout")
  public void logout(HttpServletRequest http) {
    HttpSession session = http.getSession(false);
    if (session != null) {
      session.invalidate();
    }
  }

  @GetMapping("/me")
  public AuthUserDto me() {
    AuthPrincipal principal = AuthContext.get();
    return principal == null
        ? AuthUserDto.ANONYMOUS
        : new AuthUserDto(true, principal.username(), principal.admin());
  }
}
