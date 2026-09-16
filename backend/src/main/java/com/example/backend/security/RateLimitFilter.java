package com.example.backend.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounded per-instance fallback; reverse proxy must enforce a cluster-wide limit in multi-replica
 * deployments.
 */
@Component
@Order(-200)
public class RateLimitFilter extends OncePerRequestFilter {
  private record Bucket(long minute, int count) {}

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  @Override
  protected void doFilterInternal(
      HttpServletRequest req, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (!req.getRequestURI().startsWith("/api/")) {
      chain.doFilter(req, res);
      return;
    }
    boolean auth =
        req.getRequestURI().startsWith("/api/auth/") && !req.getRequestURI().endsWith("/me");
    long minute = System.currentTimeMillis() / 60000;
    if (buckets.size() > 10000) buckets.entrySet().removeIf(e -> e.getValue().minute() < minute);
    String key = req.getRemoteAddr() + ":" + auth;
    if (buckets.size() >= 10000 && !buckets.containsKey(key)) {
      SecurityConfig.reject(res, 429, "RATE_LIMITED");
      return;
    }
    Bucket b =
        buckets.compute(
            key,
            (k, v) -> new Bucket(minute, v == null || v.minute() != minute ? 1 : v.count() + 1));
    if (b.count() > (auth ? 30 : 600)) {
      res.setHeader("Retry-After", "60");
      SecurityConfig.reject(res, 429, "RATE_LIMITED");
      return;
    }
    chain.doFilter(req, res);
  }
}
