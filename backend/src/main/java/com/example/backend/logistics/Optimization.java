package com.example.backend.logistics;

import java.util.*;

/** Explainable heuristics. Distances are geodesic estimates, not road or traffic data. */
public final class Optimization {
  private Optimization() {}

  public record Point(double latitude, double longitude) {
    public Point {
      if (!Double.isFinite(latitude)
          || !Double.isFinite(longitude)
          || Math.abs(latitude) > 90
          || Math.abs(longitude) > 180) throw new IllegalArgumentException("Invalid coordinates");
    }
  }

  public record Edge(String to, double km) {
    public Edge {
      if (to == null || to.isBlank() || !Double.isFinite(km) || km < 0)
        throw new IllegalArgumentException("Invalid edge");
    }
  }

  public record Path(List<String> nodes, double distance) {}

  public static double distance(Point a, Point b) {
    double lat = Math.toRadians(b.latitude - a.latitude),
        lon = Math.toRadians(b.longitude - a.longitude);
    double h =
        Math.pow(Math.sin(lat / 2), 2)
            + Math.cos(Math.toRadians(a.latitude))
                * Math.cos(Math.toRadians(b.latitude))
                * Math.pow(Math.sin(lon / 2), 2);
    return 6371 * 2 * Math.asin(Math.sqrt(Math.min(1, h)));
  }

  public static double warehouseScore(
      double km, int load, int capacity, int priority, int slaHours) {
    if (!Double.isFinite(km)
        || km < 0
        || capacity <= 0
        || load < 0
        || load >= capacity
        || priority < 1
        || priority > 3
        || slaHours < 1) return Double.POSITIVE_INFINITY;
    double distance = Math.min(km / 500, 1), utilization = (double) load / capacity;
    double slaRisk = Math.min((km / 35 + 0.5) / slaHours, 1);
    return 0.45 * distance + 0.30 * utilization + 0.25 * slaRisk * (priority / 3.0);
  }

  public static double driverScore(
      double km, double weight, double capacity, int workload, int priority, int slaHours) {
    if (!Double.isFinite(km)
        || !Double.isFinite(weight)
        || !Double.isFinite(capacity)
        || km < 0
        || weight <= 0
        || capacity < weight
        || workload != 0
        || priority < 1
        || priority > 3
        || slaHours < 1) return Double.POSITIVE_INFINITY;
    return 0.6 * Math.min(km / 100, 1)
        + 0.15 * (1 - weight / capacity)
        + 0.25 * Math.min(km / 35 / slaHours, 1) * (priority / 3.0);
  }

  public static int etaMinutes(double km, double speed, int stops, double conditionFactor) {
    if (!Double.isFinite(km)
        || !Double.isFinite(speed)
        || !Double.isFinite(conditionFactor)
        || km < 0
        || speed <= 0
        || stops < 0
        || conditionFactor < 1) throw new IllegalArgumentException("Invalid ETA input");
    double minutes = Math.ceil(km / speed * 60 * conditionFactor + stops * 5.0);
    if (!Double.isFinite(minutes) || minutes > Integer.MAX_VALUE)
      throw new IllegalArgumentException("ETA exceeds supported range");
    return (int) minutes;
  }

  public static Path dijkstra(Map<String, List<Edge>> graph, String start, String end) {
    if (graph == null
        || start == null
        || end == null
        || !graph.containsKey(start)
        || !graph.containsKey(end)) throw new IllegalArgumentException("Unknown node");
    // Validate the entire input, including components not reached by the search.
    for (var node : graph.entrySet()) {
      if (node.getKey() == null || node.getKey().isBlank() || node.getValue() == null)
        throw new IllegalArgumentException("Invalid graph node");
      for (var edge : node.getValue())
        if (edge == null || !graph.containsKey(edge.to()))
          throw new IllegalArgumentException("Unknown edge endpoint");
    }
    record Entry(String node, double distance) {}
    var q =
        new PriorityQueue<Entry>(
            Comparator.comparingDouble(Entry::distance).thenComparing(Entry::node));
    var distances = new HashMap<String, Double>();
    var previous = new HashMap<String, String>();
    distances.put(start, 0.0);
    q.add(new Entry(start, 0));
    while (!q.isEmpty()) {
      var current = q.poll();
      if (current.distance > distances.getOrDefault(current.node, Double.POSITIVE_INFINITY))
        continue;
      if (current.node.equals(end)) break;
      for (var edge : graph.getOrDefault(current.node, List.of())) {
        if (!graph.containsKey(edge.to))
          throw new IllegalArgumentException("Unknown edge endpoint");
        double candidate = current.distance + edge.km;
        if (!Double.isFinite(candidate))
          throw new IllegalArgumentException("Path distance overflow");
        if (candidate < distances.getOrDefault(edge.to, Double.POSITIVE_INFINITY)) {
          distances.put(edge.to, candidate);
          previous.put(edge.to, current.node);
          q.add(new Entry(edge.to, candidate));
        }
      }
    }
    if (!distances.containsKey(end))
      throw new IllegalArgumentException("Destination is unreachable");
    var nodes = new ArrayList<String>();
    for (String n = end; n != null; n = previous.get(n)) nodes.add(n);
    Collections.reverse(nodes);
    return new Path(List.copyOf(nodes), distances.get(end));
  }

  public static List<Integer> nearestNeighbor(Point start, List<Point> stops) {
    if (start == null || stops == null || stops.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid route locations");
    var remaining = new TreeSet<Integer>();
    for (int i = 0; i < stops.size(); i++) remaining.add(i);
    var route = new ArrayList<Integer>();
    Point current = start;
    while (!remaining.isEmpty()) {
      Point from = current;
      int next =
          remaining.stream()
              .min(
                  Comparator.<Integer>comparingDouble(i -> distance(from, stops.get(i)))
                      .thenComparingInt(i -> i))
              .orElseThrow();
      route.add(next);
      remaining.remove(next);
      current = stops.get(next);
    }
    return route;
  }
}
