# Executed verification

Verification performed on **17 September 2026**, exclusively in the existing `D:\projects\FleetFlow` checkout. Existing baseline: **20 JUnit tests and 2 Playwright tests**. No tests were removed or weakened.

## Results

| Check | Actual result |
|---|---|
| Backend Maven verify / executable JAR | 27 tests passed; 0 failures, errors or skipped |
| PostgreSQL integration | Native PostgreSQL 18.3, isolated disposable schemas; all three migrations applied |
| HTTP acceptance | 37 supported `/api` method/path pairs exercised; all assertions passed |
| Additional HTTP checks | OpenAPI document, unknown route 404, unsupported method 405, oversized fixed/chunked bodies 413 |
| Frontend TypeScript / Vite | Production build passed locally and in Docker |
| Browser | 4 Chrome tests against real Docker services; all passed |
| Compose | Configuration validation, both image builds and four-service startup passed |
| Container health | PostgreSQL/Redis healthy; backend database-backed readiness UP |
| Live Redis | Cache hits observed in Redis statistics after route planning |
| Secrets | Actual ignored local DB/JWT/demo credentials absent from tracked/candidate files |
| CI | Existing workflow extended with Docker builds/readiness/browser checks; new remote run not performed (no push) |

The HTTP test writes `backend/target/api-verification.txt` with observed methods, normalized paths and statuses. Its 40 pairs comprise 37 actual API endpoints, one OpenAPI endpoint and two invalid-route/method probes. Negative assertions intentionally expect 400/401/403/404/405/409; these are successful checks, not failed endpoints. Oversized body probes run directly and are additional assertions. Endpoint coverage is not a claim that every permutation of each endpoint was tested.

## Verified behavior

- Authentication: registration, login, bad credentials, protected access, refresh rotation, concurrent refresh, logout and account-change revocation, hashed single-use reset tokens. Signed malformed version claims return 401.
- Authorization: ADMIN, OPERATOR, DRIVER and CUSTOMER checks; customer cannot edit users or read audit data; operator cannot list users; unrelated customers cannot read orders/shipments or delete addresses; assigned drivers can read their order and manage only permitted availability.
- Fleet resources: persisted vehicle/driver creation, missing vehicle IDs, maintenance/offline/on-leave exclusion, insufficient weight capacity, committed-resource changes rejected and linked driver role protection. No fleet or organization aggregate exists, so tenant/fleet-ID tests are not applicable.
- Delivery: create catalog/depot/stock/resources → customer order → picking → packed → assign → report location/ETA → receive SSE → dispatch → in transit → out for delivery → delivered → stock deduction → analytics → logout. Cancellation, failed delivery, returns and duplicate events are also tested.
- Concurrency: final-stock contention and shared retry identity; only one order wins a single driver; two feasible orders use two distinct drivers even while the first assignment remains uncommitted.
- Routes: known geodesic distance/ETA, indirect shortest path, unreachable/invalid graph, disconnected malformed edge, overflow, empty/single/duplicate/50-stop deterministic behavior, API maximum-size validation and live planning form.
- Browser: customer creates/cancels an actual order; admin dashboard/orders/SSE; mobile customer layout/navigation; route planning success and invalid coordinates. Screenshots are local ignored test artifacts, not fabricated product metrics.
- Database: transactional writes and constraints verified against PostgreSQL; container records survive application rebuild/recreation using its named volume. Native development data was not reset.

## Bugs found and fixed

1. Assignment locked all eligible drivers, unnecessarily preventing another feasible concurrent assignment. It now ranks candidates then locks/rechecks one pair at a time.
2. Shortest-path validation skipped malformed disconnected components; null plan stops could fail unexpectedly. Entire graph/input validation now rejects these requests safely.
3. ETA stop arithmetic could overflow, and non-finite scoring inputs could bypass rejection. Inputs and result ranges are now checked.
4. Driver account checks raced role administration and allowed inactive linked accounts; the user row is now locked and active DRIVER status required.
5. Warehouses could be relocated while reserved orders depended on their location; relocation is now rejected while workload is nonzero.
6. Missing/malformed JWT version claims could produce server errors; they now fail authentication. SSE initial version capture is tied to the authenticated request and cannot consume a permit before a failed lookup.
7. Address deletion and alert resolution reported success for nonexistent targets; they now return 404. Address ownership is enforced in the deletion predicate.
8. Security/controller errors lacked a shared correlation envelope, and API body size was unbounded at the application boundary. Shared errors, generated request IDs, safe access logs and a 256 KiB cap are implemented.
9. A broad route-map SVG style enlarged its location icon; map sizing now applies only to the map SVG.

## Scope and limitations

- No fake benchmark, savings, ETA accuracy or production adoption metrics were added. No sustained load test or independent penetration test was performed.
- SMTP delivery remains unverified against a real mail service; recovery lifecycle tests mock only the delivery adapter.
- The shared native PostgreSQL service was not stopped to simulate an outage.
- Routes and ETAs use geodesic distances and fixed planning speed, not road or live traffic data. Multi-stop plans are heuristic, on demand and unsaved; assignment remains one shipment per driver.
- No distinct fleet/tenant, working-hours/fuel model, batch vehicle-routing solver, ML model or cloud deployment exists.
- Test Docker project: `fleetflow-verification`, frontend localhost:15173, API localhost:18080, PostgreSQL localhost:15433. It uses its own volume and existing opt-in demo fixtures. Do not use test/demo accounts in production.

## Performance review

The original driver query locked the entire eligible set; the new regression demonstrates progress for two assignments without claiming throughput gains. Candidate ranking costs O(D log D), with up to D recheck queries under contention. Warehouse allocation still locks all candidate warehouses. Nearest-neighbor planning is O(S²), bounded to 50 API stops; graph requests are bounded to 200 nodes and 200 outgoing edges per node and the request-byte cap. Additive indexes match real ownership/token/tracking queries. Larger-fleet candidate pruning and measured load testing remain future work.
