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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import no.bekk.dbscheduler.ui.users.dto.AuthUserDto;
import no.bekk.dbscheduler.ui.users.dto.LoginRequest;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

class AuthControllerTest {

  private final AuthService auth = mock(AuthService.class);
  private final AuthController controller = new AuthController(auth);

  private static final AuthPrincipal ALICE = new AuthPrincipal("u1", "alice", false);

  @Test
  void loginStoresPrincipalAndRotatesSessionId() {
    when(auth.authenticate("alice", "pw")).thenReturn(Optional.of(ALICE));
    MockHttpServletRequest request = spy(new MockHttpServletRequest());
    request.setSession(new MockHttpSession()); // pre-login session whose id must not survive

    AuthUserDto dto = controller.login(new LoginRequest("alice", "pw"), request);

    assertThat(dto.authenticated()).isTrue();
    assertThat(dto.username()).isEqualTo("alice");
    verify(request).changeSessionId(); // session fixation defense
    assertThat(request.getSession().getAttribute(AuthPrincipal.SESSION_KEY)).isEqualTo(ALICE);
  }

  @Test
  void loginFromFreshAnonymousRequestCreatesSession() {
    when(auth.authenticate("alice", "pw")).thenReturn(Optional.of(ALICE));
    // a first-ever login has no session to rotate — changeSessionId() would throw
    MockHttpServletRequest request = new MockHttpServletRequest();

    AuthUserDto dto = controller.login(new LoginRequest("alice", "pw"), request);

    assertThat(dto.authenticated()).isTrue();
    assertThat(request.getSession().getAttribute(AuthPrincipal.SESSION_KEY)).isEqualTo(ALICE);
  }

  @Test
  void badCredentialsGive401WithoutMessageLeak() {
    when(auth.authenticate("alice", "nope")).thenReturn(Optional.empty());
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertThatExceptionOfType(ResponseStatusException.class)
        .isThrownBy(() -> controller.login(new LoginRequest("alice", "nope"), request))
        .matches(e -> e.getStatusCode() == HttpStatus.UNAUTHORIZED);
  }

  @Test
  void meIsAnonymousWithoutContext() {
    AuthUserDto dto = controller.me();
    assertThat(dto.authenticated()).isFalse();
    assertThat(dto.username()).isNull();
  }

  @Test
  void meReflectsContextPrincipal() {
    AuthContext.set(ALICE);
    try {
      AuthUserDto dto = controller.me();
      assertThat(dto.authenticated()).isTrue();
      assertThat(dto.username()).isEqualTo("alice");
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void logoutInvalidatesSession() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpSession session = new MockHttpSession();
    session.setAttribute(AuthPrincipal.SESSION_KEY, ALICE);
    request.setSession(session);

    controller.logout(request);

    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void logoutWithoutSessionIsNoop() {
    controller.logout(new MockHttpServletRequest()); // must not throw
  }
}
