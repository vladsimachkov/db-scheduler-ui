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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import no.bekk.dbscheduler.ui.users.model.UserAccount;
import no.bekk.dbscheduler.ui.users.repo.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthSessionFilterTest {

  private final AuthSessionFilter filter = new AuthSessionFilter();

  private static final AuthPrincipal ALICE = new AuthPrincipal("u1", "alice", false);

  private static MockHttpServletRequest request(String method, String path) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    // servletPath is what the container hands over after decoding and normalizing — the filter
    // must key on that, never on the raw request URI.
    request.setServletPath(path);
    return request;
  }

  private static MockHttpServletRequest authenticated(String method, String uri) {
    MockHttpServletRequest request = request(method, uri);
    request.getSession().setAttribute(AuthPrincipal.SESSION_KEY, ALICE);
    return request;
  }

  @Test
  void anonymousApiRequestGets401() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request("GET", "/db-scheduler-api/sql-jobs"), response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("Authentication required");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void publicApiPathsPassAnonymously() throws Exception {
    for (String path :
        List.of(
            "/db-scheduler-api/config",
            "/db-scheduler-api/auth/login",
            "/db-scheduler-api/auth/me",
            "/db-scheduler-api/auth/logout")) {
      MockFilterChain chain = new MockFilterChain();
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request("GET", path), response, chain);
      assertThat(response.getStatus()).as(path).isEqualTo(200);
      assertThat(chain.getRequest()).as(path).isNotNull();
    }
  }

  @Test
  void nonApiPathsAlwaysPass() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request("GET", "/db-scheduler"), new MockHttpServletResponse(), chain);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void contextPathPrefixedApiIsStillGuarded() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request("GET", "/myapp/api/db-scheduler-api/sql-jobs"), response, chain);
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void percentEncodedMarkerCannotBypassTheGuard() throws Exception {
    // `/%64b-scheduler-api/...` decodes to `/db-scheduler-api/...` for Spring's router; the raw
    // URI must not be what the filter matches on.
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/%64b-scheduler-api/tasks/all");
    request.setRequestURI("/%64b-scheduler-api/tasks/all"); // raw, as the container sees it
    request.setServletPath("/db-scheduler-api/tasks/all"); // decoded, what Spring routes on
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void similarLookingPathsAreNotMistakenForTheApi() throws Exception {
    for (String path :
        List.of("/my-db-scheduler-api-proxy/x", "/db-scheduler-api-bar/x", "/db-scheduler")) {
      MockFilterChain chain = new MockFilterChain();
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request("GET", path), response, chain);
      assertThat(response.getStatus()).as(path).isEqualTo(200);
      assertThat(chain.getRequest()).as(path).isNotNull();
    }
  }

  @Test
  void sessionPrincipalPassesAndPopulatesContext() throws Exception {
    AtomicReference<AuthPrincipal> seen = new AtomicReference<>();
    FilterChain capture = (rq, rs) -> seen.set(AuthContext.get());

    filter.doFilter(
        authenticated("GET", "/db-scheduler-api/sql-jobs"), new MockHttpServletResponse(), capture);

    assertThat(seen.get()).isEqualTo(ALICE);
    assertThat(AuthContext.get()).isNull(); // cleared after the request
  }

  @Test
  void mutatingWithoutCsrfHeaderIs403() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(authenticated("POST", "/db-scheduler-api/sql-jobs"), response, chain);

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void mutatingWithCsrfHeaderPasses() throws Exception {
    MockHttpServletRequest request = authenticated("POST", "/db-scheduler-api/sql-jobs");
    request.addHeader(AuthSessionFilter.CSRF_HEADER, "XMLHttpRequest");
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void garbageSessionAttributeTreatedAsAnonymous() throws Exception {
    MockHttpServletRequest request = request("GET", "/db-scheduler-api/sql-jobs");
    request.getSession().setAttribute(AuthPrincipal.SESSION_KEY, "not-a-principal");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  // --- revalidation against the account store (constructor with UserRepository) ---

  private static UserAccount account(boolean admin, boolean enabled) {
    Instant now = Instant.now();
    return new UserAccount("u1", "alice", "hash", admin, enabled, now, now);
  }

  @Test
  void deletedOrDisabledUserSessionIsRejected() throws Exception {
    UserRepository users = mock(UserRepository.class);
    AuthSessionFilter revalidating = new AuthSessionFilter(users);

    when(users.findById("u1")).thenReturn(Optional.empty());
    MockHttpServletResponse response = new MockHttpServletResponse();
    revalidating.doFilter(
        authenticated("GET", "/db-scheduler-api/sql-jobs"), response, new MockFilterChain());
    assertThat(response.getStatus()).isEqualTo(401);

    when(users.findById("u1")).thenReturn(Optional.of(account(false, false)));
    MockHttpServletResponse disabledResponse = new MockHttpServletResponse();
    revalidating.doFilter(
        authenticated("GET", "/db-scheduler-api/sql-jobs"),
        disabledResponse,
        new MockFilterChain());
    assertThat(disabledResponse.getStatus()).isEqualTo(401);
  }

  @Test
  void revalidationRefreshesTheAdminFlagFromTheDatabase() throws Exception {
    UserRepository users = mock(UserRepository.class);
    // session still claims admin; the account was demoted in the meantime
    when(users.findById("u1")).thenReturn(Optional.of(account(false, true)));
    AuthSessionFilter revalidating = new AuthSessionFilter(users);

    AtomicReference<AuthPrincipal> seen = new AtomicReference<>();
    FilterChain capture = (rq, rs) -> seen.set(AuthContext.get());
    revalidating.doFilter(
        authenticated("GET", "/db-scheduler-api/sql-jobs"), new MockHttpServletResponse(), capture);

    assertThat(seen.get()).isNotNull().isEqualTo(new AuthPrincipal("u1", "alice", false));
  }
}
