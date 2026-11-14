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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import no.bekk.dbscheduler.ui.users.model.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Session gate for the REST API when the users feature is enabled. Deliberately Spring
 * Security-free so it can sit inside host apps that run their own security on top.
 *
 * <p>Only paths whose decoded path contains {@code /db-scheduler-api} as a full segment are guarded
 * (robust against both {@code server.servlet.context-path} and a custom {@code
 * db-scheduler-ui.context-path}); SPA and static paths always pass — the SPA renders the login
 * screen client-side. The guard runs on REQUEST dispatch only; async dispatches skip it (the
 * initial dispatch is always authenticated), so a future async endpoint must capture the principal
 * before starting the async work.
 *
 * <p>CSRF: mutating requests must carry {@code X-Requested-With} — a cross-site form post cannot
 * set custom headers, and the session cookie is HttpOnly. The frontend apiClient always sends it.
 */
public class AuthSessionFilter extends OncePerRequestFilter {

  public static final String CSRF_HEADER = "X-Requested-With";

  private static final String API_MARKER = "/db-scheduler-api";
  private static final Set<String> PUBLIC_API_PATHS =
      Set.of("/config", "/auth/login", "/auth/me", "/auth/logout");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    // Never match on getRequestURI(): it is the RAW, undecoded URI while Spring routes on the
    // decoded path — `/%64b-scheduler-api/...` would slip past a raw-string check and still reach
    // the API handlers. servletPath+pathInfo is container-decoded and normalized.
    String apiSubPath = apiSubPath(decodedPath(request));
    if (apiSubPath == null) {
      filterChain.doFilter(request, response);
      return;
    }

    AuthPrincipal principal = currentPrincipal(request);
    boolean publicPath = PUBLIC_API_PATHS.contains(apiSubPath);

    if (principal == null && !publicPath) {
      writeProblem(response, HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required");
      return;
    }
    if (principal != null
        && isMutating(request.getMethod())
        && request.getHeader(CSRF_HEADER) == null) {
      writeProblem(
          response, HttpStatus.FORBIDDEN, "Forbidden", "Missing " + CSRF_HEADER + " header");
      return;
    }

    if (principal == null) {
      filterChain.doFilter(request, response);
      return;
    }
    AuthContext.set(principal);
    try {
      filterChain.doFilter(request, response);
    } finally {
      AuthContext.clear();
    }
  }

  /** The request path within the application, decoded and normalized by the container. */
  private static String decodedPath(HttpServletRequest request) {
    String servletPath = request.getServletPath();
    String pathInfo = request.getPathInfo();
    String path = (servletPath == null ? "" : servletPath) + (pathInfo == null ? "" : pathInfo);
    return path.isEmpty() ? "/" : path;
  }

  /**
   * The path after the {@code /db-scheduler-api} marker (which may sit under a context path), or
   * {@code null} when the path is not API. The marker must occupy a full path segment, so neither
   * {@code /my-db-scheduler-api-proxy} nor {@code /db-scheduler-api-bar} is mistaken for the API.
   */
  private static String apiSubPath(String path) {
    int index = path.indexOf(API_MARKER + "/");
    if (index >= 0) {
      return path.substring(index + API_MARKER.length());
    }
    return path.endsWith(API_MARKER) ? "/" : null;
  }

  private static boolean isMutating(String method) {
    return switch (method.toUpperCase()) {
      case "POST", "PUT", "DELETE", "PATCH" -> true;
      default -> false;
    };
  }

  private static AuthPrincipal currentPrincipal(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return null;
    }
    Object value = session.getAttribute(AuthPrincipal.SESSION_KEY);
    return value instanceof AuthPrincipal principal ? principal : null;
  }

  private static void writeProblem(
      HttpServletResponse response, HttpStatus status, String title, String detail)
      throws IOException {
    // Same problem-detail shape the frontend apiClient already understands.
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write("{\"title\":\"" + title + "\",\"detail\":\"" + detail.replace("\"", "\\\"") + "\"}");
  }
}
