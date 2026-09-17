# Algorithms

## Geographic distance

Coordinates are checked for finite values and legal latitude/longitude ranges. Haversine distance uses an Earth radius of 6371 km. Floating-point rounding is clamped before inverse sine. Distances describe the shortest great-circle separation, not streets, traffic or travel restrictions.

## Warehouse selection

Hard constraints: ACTIVE/BUSY status, `current_load < capacity`, and sufficient available stock for every item at one warehouse. The lowest score wins; ties use warehouse UUID.

For distance `d` km, current load `l`, capacity `c`, priority `p` in 1–3 and SLA `s` hours:

```
D = min(d / 500, 1)
U = l / c
R = min((d / 35 + 0.5) / s, 1)
score = 0.45 D + 0.30 U + 0.25 R (p / 3)
```

Availability is a hard filter, not a term that could be outweighed by proximity. The SLA term estimates travel and 30 minutes of handling. Priority increases the weight of SLA risk. The system does not guarantee that an accepted SLA can be met.

For W warehouses and I distinct products, ranking costs O(W log W) and stock checks O(WI), plus database I/O. Coordinates and load used for ranking are a snapshot; eligibility and stock are rechecked under lock. No split fulfillment is attempted. Full/unavailable warehouses are rejected. Failure rolls back the entire order.

## Driver selection

Hard constraints: AVAILABLE driver, zero active workload, AVAILABLE vehicle and capacity at least the total order weight. Linked accounts must be active DRIVER users. One vehicle belongs to at most one driver.

```
D = min(driver_to_warehouse_km / 100, 1)
C = 1 - order_weight / vehicle_capacity
R = min(driver_to_warehouse_km / 35 / sla_hours, 1)
score = 0.60 D + 0.15 C + 0.25 R (priority / 3)
```

The smallest score wins; ties use driver UUID. Larger spare capacity is penalized slightly to conserve larger vehicles. Ranking is O(D log D) over eligible drivers, followed by up to D candidate recheck queries under contention. Row locks and a rechecked zero-workload constraint protect assignments. No batching or multi-shipment driver loading is supported.

## Weighted graph shortest path

`Optimization.dijkstra` accepts an adjacency list of nonnegative weighted edges. A priority queue and stale-entry check compute shortest distance and reconstruct predecessor nodes. Unknown endpoints, negative/nonfinite weights and unreachable targets are rejected. Complexity is O((V+E) log V), space O(V+E), assuming a normal sparse graph. The HTTP endpoint bounds graph size.

Shipment routes currently construct a two-node graph with a Haversine edge. This intentionally exposes the limitation: applying Dijkstra to that graph does not turn it into a real road route. A* is not used because this representation offers no benefit from a search heuristic.

## Multi-stop routing

`/api/routes/plan` uses nearest neighbor over at most 50 supplied stops. It repeatedly chooses the closest unvisited stop, with original index as a stable tie-breaker. Complexity O(n²); no optimal TSP guarantee and no return-to-origin leg. The response contains stop indices, total estimated distance and travel time.

## ETA

```
minutes = ceil(distance_km / speed_kmh * 60 * condition_factor + stops * 5)
```

Initial shipment planning uses 25 km/h for bikes and 35 km/h for other vehicles, one stop and condition factor 1. Driver position updates use the same vehicle-specific speed for the remaining leg. No traffic data is incorporated. The origin of an initial route is the warehouse; driver-to-warehouse approach and picking duration are not included. Clients must treat ETA as an estimate.

## Cache

Redis stores Haversine values keyed by a versioned hash of the coordinate pair, TTL one hour. Invalid cached values or Redis exceptions trigger local computation and a 30-second cache bypass. Stock and assignment decisions never read Redis.

## September 2026 validation improvements

The entire submitted graph is validated before shortest-path search, including disconnected components and start=end queries. Null/blank nodes, null edges, unknown endpoints, negative/non-finite weights and accumulated distance overflow are rejected. ETA arithmetic uses floating-point stop overhead and rejects values outside its integer-minute range. Invalid non-finite scoring inputs cannot become feasible candidates.

Nearest-neighbor plans remain deterministic by original stop index for ties; duplicate coordinates are separate visits and each adds five handling minutes. The API accepts 1–50 stops; the pure algorithm also supports an empty list. The console can calculate these plans using the live endpoint. Plans are open paths, are not persisted, and do not automatically assign orders. Dijkstra solves the supplied nonnegative graph; the multi-stop heuristic does not promise a globally optimal travelling-salesperson solution. Assignment supports one active shipment per driver, not a batched vehicle-routing solver.
