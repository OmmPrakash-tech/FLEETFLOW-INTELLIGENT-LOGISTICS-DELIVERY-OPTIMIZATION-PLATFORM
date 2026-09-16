package com.example.backend.logistics;

import com.example.backend.security.Actor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/routes")
public class RouteController {
  public record Plan(
      @NotNull @Valid Requests.Position start,
      @NotEmpty @Size(max = 50) @Valid List<Requests.Position> stops,
      @Min(1) @Max(120) double speedKmh) {}

  public record Graph(
      @NotNull @Size(min = 1, max = 200) Map<String, List<Optimization.Edge>> nodes,
      @NotBlank String start,
      @NotBlank String end) {}

  private final DistanceCache cache;

  public RouteController(DistanceCache cache) {
    this.cache = cache;
  }

  @PostMapping("/plan")
  public Object plan(@Valid @RequestBody Plan r) {
    Actor.current().requireStaff();
    var start = new Optimization.Point(r.start.latitude(), r.start.longitude());
    var stops =
        r.stops.stream().map(p -> new Optimization.Point(p.latitude(), p.longitude())).toList();
    var order = Optimization.nearestNeighbor(start, stops);
    double km = 0;
    var current = start;
    for (int i : order) {
      km += cache.distance(current, stops.get(i));
      current = stops.get(i);
    }
    return Map.of(
        "stopOrder",
        order,
        "distanceKm",
        km,
        "durationMinutes",
        Optimization.etaMinutes(km, r.speedKmh, stops.size(), 1),
        "source",
        "GEODESIC_ESTIMATE",
        "algorithm",
        "NEAREST_NEIGHBOR");
  }

  @PostMapping("/shortest-path")
  public Object shortest(@Valid @RequestBody Graph r) {
    Actor.current().requireStaff();
    if (r.nodes.values().stream()
        .anyMatch(e -> e == null || e.size() > 200 || e.stream().anyMatch(Objects::isNull)))
      throw new IllegalArgumentException("Invalid graph");
    return Optimization.dijkstra(r.nodes, r.start, r.end);
  }
}
