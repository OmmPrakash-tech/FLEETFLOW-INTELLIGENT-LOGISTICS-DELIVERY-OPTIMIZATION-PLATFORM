package com.example.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.backend.logistics.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class DistanceCacheTests {
  @Test
  void redisFailureFallsBackAndOpensCircuit() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new RuntimeException("Unavailable"));
    var metrics = new SimpleMeterRegistry();
    var cache = new DistanceCache(redis, metrics);
    var a = new Optimization.Point(0, 0);
    var b = new Optimization.Point(0, 1);
    assertEquals(111.195, cache.distance(a, b), .01);
    assertEquals(111.195, cache.distance(a, b), .01);
    verify(redis, times(1)).opsForValue();
    assertEquals(1, metrics.counter("fleetflow.redis.fallbacks").count());
  }
}
