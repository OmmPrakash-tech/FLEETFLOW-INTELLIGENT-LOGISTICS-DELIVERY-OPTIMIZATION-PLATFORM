# Transactions, locks and retries

## Inventory reservations

`OrderService.create` is one transaction at PostgreSQL's default READ COMMITTED isolation. It acquires a transaction advisory lock for the actor/operation/retry key, writes the order, locks candidate warehouses in UUID order, and locks inventory in product UUID order. It checks all required stock before writing any reservations. This conservative ordering prevents two valid reservations from consuming the same final units and reduces deadlock risk across multi-product orders.

Quantity and reserved stock have database CHECK constraints. All stock adjustments acquire the warehouse lock before changing inventory. Reservation release, deduction and return restocking are coupled to legal order transitions and protected by the order row lock.

## Assignments

Assignment locks the order, ranks eligible drivers, then locks and rechecks one candidate driver/vehicle pair at a time using `FOR UPDATE ... SKIP LOCKED`. Driver workload is constrained to 0–1; status and vehicle capacity are checked before assignment. Shipment order ID is unique. Driver/vehicle status updates and shipment/route creation commit together.

SKIP LOCKED may return no eligible driver while another assignment temporarily holds locks, even when some drivers will become available afterward. The caller receives a retryable 409, never an unsafe assignment. Unselected candidates remain unlocked. A regression test holds the first assignment uncommitted and verifies a second order can use a spare driver. Candidate scores are a point-in-time heuristic; feasibility is rechecked under lock.

## Lifecycle and position reports

Transitions lock the order first. Position reports lock active assigned orders in UUID order before the driver. This aligns lock ordering with dispatch/delivery. A completed order cannot deduct stock again; a returned order cannot restock twice. Delivered-order returns do not release a driver that may already have a new assignment.

## Idempotency

The durable record contains actor, key, operation, SHA-256 request fingerprint and resulting resource UUID. A transaction-scoped advisory lock serializes requests with identical keys across app replicas. The durable row is inserted only on success, in the same transaction as the business changes. A rollback leaves no successful retry record. The primary key is a second safeguard.

Fingerprint mismatches return 409. Results return the original resource identity; the API does not preserve an entire historical HTTP response body. These records currently have no automatic expiry.

## Authentication

Refresh and reset operations first identify the token owner, lock the user, and then atomically consume the hashed token. Consumption is rechecked after locking. Logout/password/account changes increment the user's token version and delete outstanding tokens. Requests check live user state rather than trusting a stale role embedded in a JWT.

## Outbox

Business events commit with the order transaction. Consumers lock unprocessed events with SKIP LOCKED and commit alert creation and processed markers together. A unique active-alert index deduplicates repeated events. This supports at-least-once internal consumption without duplicate active alerts; it is not an exactly-once external-message claim.

## Tests

Parallel tests synchronize independent threads at a barrier and assert that only one reservation/assignment wins. Tests also retry duplicate requests and verify shared identity, unchanged stock, and a single delivery event. They use real PostgreSQL transactions in a disposable schema, not mocked locks or H2 semantics.
