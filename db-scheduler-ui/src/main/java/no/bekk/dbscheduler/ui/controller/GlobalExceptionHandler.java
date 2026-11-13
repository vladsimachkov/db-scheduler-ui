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
package no.bekk.dbscheduler.ui.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions thrown by the db-scheduler-ui controllers to RFC 7807 problem responses.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} so Spring MVC framework exceptions (missing
 * request parameters, unreadable bodies, type mismatches, ...) keep their proper 4xx status instead
 * of being downgraded to 500 by a catch-all handler.
 *
 * <p>Scoped to the {@code no.bekk.dbscheduler.ui.controller} package so it never interferes with
 * the host application's own error handling.
 */
@RestControllerAdvice(basePackages = "no.bekk.dbscheduler.ui.controller")
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  public ProblemDetail handleResponseStatus(ResponseStatusException e) {
    ProblemDetail problem = ProblemDetail.forStatus(e.getStatusCode());
    if (e.getReason() != null) {
      problem.setDetail(e.getReason());
    }
    return problem;
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    if (statusCode.is5xxServerError()) {
      // Not a known client error: log it before the generic error response goes out.
      log.error("Unhandled exception in db-scheduler-ui controller", ex);
    }
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
  }
}
