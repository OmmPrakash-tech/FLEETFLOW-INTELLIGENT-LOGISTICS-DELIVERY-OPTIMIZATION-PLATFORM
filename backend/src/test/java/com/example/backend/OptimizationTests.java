package com.example.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.example.backend.logistics.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class OptimizationTests {
  @Test
  void rejectsInvalidDisconnectedComponentsAndOverflow() {
    var graph =
        Map.of(
            "A", List.<Optimization.Edge>of(), "B", List.of(new Optimization.Edge("missing", 1)));
    assertThrows(IllegalArgumentException.class, () -> Optimization.dijkstra(graph, "A", "A"));
    assertThrows(IllegalArgumentException.class, () -> new Optimization.Edge(null, 1));
    assertThrows(
        IllegalArgumentException.class, () -> Optimization.etaMinutes(Double.MAX_VALUE, 1, 0, 1));
    assertThrows(
        IllegalArgumentException.class, () -> Optimization.etaMinutes(0, 35, Integer.MAX_VALUE, 1));
    assertEquals(Double.POSITIVE_INFINITY, Optimization.driverScore(Double.NaN, 1, 10, 0, 1, 24));
    assertEquals(Double.POSITIVE_INFINITY, Optimization.driverScore(1, 1, 10, 0, 1, 0));
  }

  @Test
  void emptySingleDuplicateAndFiftyStopsRemainDeterministic() {
    var start = new Optimization.Point(20, 85);
    assertEquals(List.of(), Optimization.nearestNeighbor(start, List.of()));
    assertEquals(List.of(0), Optimization.nearestNeighbor(start, List.of(start)));
    var duplicates = Collections.nCopies(50, start);
    assertEquals(
        java.util.stream.IntStream.range(0, 50).boxed().toList(),
        Optimization.nearestNeighbor(start, duplicates));
    assertThrows(
        IllegalArgumentException.class,
        () -> Optimization.nearestNeighbor(start, Arrays.asList(start, null)));
  }

  @Test
  void legalTransitionsRejectSkippingAndTerminalChanges() {
    assertTrue(OrderState.CREATED.allows(OrderState.CONFIRMED));
    assertFalse(OrderState.CREATED.allows(OrderState.DELIVERED));
    assertFalse(OrderState.CANCELLED.allows(OrderState.CONFIRMED));
    assertTrue(OrderState.DELIVERED.allows(OrderState.RETURN_REQUESTED));
    assertThrows(RuntimeException.class, () -> OrderState.PACKED.require(OrderState.DELIVERED));
  }

  @Test
  void knownGeodesicAndEta() {
    var a = new Optimization.Point(0, 0);
    var b = new Optimization.Point(0, 1);
    assertEquals(111.195, Optimization.distance(a, b), 0.01);
    assertEquals(65, Optimization.etaMinutes(35, 35, 1, 1));
    assertThrows(IllegalArgumentException.class, () -> Optimization.etaMinutes(2, 0, 1, 1));
  }

  @Test
  void shortestRouteUsesIndirectPathAndRejectsUnreachable() {
    var graph =
        Map.of(
            "A",
            List.of(new Optimization.Edge("B", 2), new Optimization.Edge("C", 9)),
            "B",
            List.of(new Optimization.Edge("C", 3)),
            "C",
            List.<Optimization.Edge>of(),
            "D",
            List.<Optimization.Edge>of());
    var route = Optimization.dijkstra(graph, "A", "C");
    assertEquals(List.of("A", "B", "C"), route.nodes());
    assertEquals(5, route.distance());
    assertThrows(IllegalArgumentException.class, () -> Optimization.dijkstra(graph, "A", "D"));
    assertThrows(IllegalArgumentException.class, () -> new Optimization.Edge("B", -1));
  }

  @Test
  void scoringExcludesFullWarehouseAndOverCapacityVehicle() {
    assertEquals(Double.POSITIVE_INFINITY, Optimization.warehouseScore(1, 10, 10, 1, 24));
    assertEquals(Double.POSITIVE_INFINITY, Optimization.driverScore(1, 20, 10, 0, 1, 24));
    assertTrue(
        Optimization.warehouseScore(10, 0, 100, 2, 24)
            < Optimization.warehouseScore(400, 90, 100, 2, 24));
  }

  @Test
  void multiStopVisitsEachLocationOnce() {
    assertEquals(
        List.of(1, 0),
        Optimization.nearestNeighbor(
            new Optimization.Point(0, 0),
            List.of(new Optimization.Point(0, 2), new Optimization.Point(0, 1))));
  }
}
