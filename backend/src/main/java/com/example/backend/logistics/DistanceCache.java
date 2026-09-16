package com.example.backend.logistics;

import com.example.backend.security.AuthService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis only caches immutable coordinate calculations; authoritative inventory never depends on it.
 */
@Component
public class DistanceCache {
  private final StringRedisTemplate redis;
  private final MeterRegistry metrics;
  private volatile long retryAfter;

  public DistanceCache(StringRedisTemplate redis, MeterRegistry metrics) {
    this.redis = redis;
    this.metrics = metrics;
  }

  public double distance(Optimization.Point start, Optimization.Point end) {
    String key = "fleetflow:distance:v1:" + AuthService.hash(start + ":" + end);
    if (System.currentTimeMillis() < retryAfter) return Optimization.distance(start, end);
    try {
      String cached = redis.opsForValue().get(key);
      if (cached != null) {
        double value = Double.parseDouble(cached);
        if (Double.isFinite(value) && value >= 0) {
          metrics.counter("fleetflow.route.cache.hits").increment();
          return value;
        }
      }
      double value = Optimization.distance(start, end);
      redis.opsForValue().set(key, Double.toString(value), Duration.ofHours(1));
      return value;
    } catch (RuntimeException e) {
      retryAfter = System.currentTimeMillis() + 30000;
      metrics.counter("fleetflow.redis.fallbacks").increment();
      return Optimization.distance(start, end);
    }
  }
}
