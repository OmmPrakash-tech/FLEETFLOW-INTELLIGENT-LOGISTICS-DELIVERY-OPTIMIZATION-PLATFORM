package com.example.backend.common;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bound API payloads before parsing, including chunked requests. No bodies or query strings logged.
 */
@Component
@Order(-210)
public class RequestBoundaryFilter extends OncePerRequestFilter {
  public static final int MAX_BODY = 262144;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    String requestId = UUID.randomUUID().toString();
    req.setAttribute("requestId", requestId);
    res.setHeader("X-Request-ID", requestId);
    long started = System.nanoTime();
    try {
      if (req.getRequestURI().startsWith("/api/")) {
        if (req.getContentLengthLong() > MAX_BODY) {
          ApiErrors.write(req, res, 413, "PAYLOAD_TOO_LARGE");
          return;
        }
        byte[] body = req.getInputStream().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) {
          ApiErrors.write(req, res, 413, "PAYLOAD_TOO_LARGE");
          return;
        }
        chain.doFilter(
            new HttpServletRequestWrapper(req) {
              @Override
              public ServletInputStream getInputStream() {
                var input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                  @Override
                  public int read() {
                    return input.read();
                  }

                  @Override
                  public int read(byte[] b, int off, int len) {
                    return input.read(b, off, len);
                  }

                  @Override
                  public boolean isFinished() {
                    return input.available() == 0;
                  }

                  @Override
                  public boolean isReady() {
                    return true;
                  }

                  @Override
                  public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException(
                        "Nonblocking request body reads are unsupported");
                  }
                };
              }

              @Override
              public BufferedReader getReader() {
                return new BufferedReader(
                    new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
              }
            },
            res);
      } else chain.doFilter(req, res);
    } finally {
      // For SSE, latency measures subscription setup, not the stream's lifetime.
      LoggerFactory.getLogger(RequestBoundaryFilter.class)
          .info(
              JSON.writeValueAsString(
                  java.util.Map.of(
                      "operation",
                      "http_request",
                      "requestId",
                      requestId,
                      "method",
                      req.getMethod(),
                      "path",
                      req.getRequestURI(),
                      "status",
                      res.getStatus(),
                      "latencyMs",
                      (System.nanoTime() - started) / 1_000_000)));
    }
  }
}
