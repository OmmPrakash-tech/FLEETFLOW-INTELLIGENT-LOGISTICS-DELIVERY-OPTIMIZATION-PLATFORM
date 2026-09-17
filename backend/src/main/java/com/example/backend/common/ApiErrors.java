package com.example.backend.common;

import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/** Shared envelope for servlet filters and controller failures. */
public final class ApiErrors {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ApiErrors() {}

  public static Map<String, Object> body(
      HttpServletRequest req, int status, String code, String message) {
    return Map.of(
        "timestamp",
        Instant.now().toString(),
        "status",
        status,
        "code",
        code,
        "message",
        message,
        "path",
        req.getRequestURI(),
        "requestId",
        String.valueOf(req.getAttribute("requestId")));
  }

  public static void write(HttpServletRequest req, HttpServletResponse res, int status, String code)
      throws IOException {
    res.setStatus(status);
    res.setContentType("application/json");
    res.getWriter().write(JSON.writeValueAsString(body(req, status, code, code.replace('_', ' '))));
  }
}
