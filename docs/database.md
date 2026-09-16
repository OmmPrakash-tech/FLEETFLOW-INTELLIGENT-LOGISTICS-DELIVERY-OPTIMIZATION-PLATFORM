# Database

PostgreSQL database: `fleetflow`. Flyway is the sole schema migration mechanism. V1 creates the domain; V2 adds outbox processing markers and supporting indexes. Migrations are applied at startup and checksum validated; applied migration files must not be edited.

| Table | Purpose |
| --- | --- |
| app_user, auth_token | Identity, account status, revocation version, hashed refresh/reset tokens |
| address | Customer-owned addresses |
| product | SKU, weight and price |
| warehouse, inventory | Fulfillment capacity and stock ledger balances |
| vehicle, driver | Capacity, availability, location and exclusive assignment |
| customer_order, order_item | Order lifecycle, destination and price snapshots |
| route, shipment, shipment_event | Estimates, delivery assignment and chronological history |
| idempotency | Actor-scoped retry key, operation, request fingerprint and resulting ID |
| audit_log | Actor, action, resource and timestamp; no credentials |
| alert | Persisted exceptions, unique active alert per type/resource |
| outbox_event | Atomic internal events and consumer completion markers |

## Constraints

- Inventory: quantity ≥ 0; 0 ≤ reserved ≤ quantity. Available stock is calculated as `quantity - reserved` and never independently stored.
- Warehouse: positive capacity, load between zero and capacity. Capacity means active fulfillment slots, not shelf volume.
- Vehicle: positive kg capacity, unique registration.
- Driver: unique linked user and vehicle, workload 0 or 1.
- Shipment: one shipment per order; foreign keys require existing warehouse, driver and route records.
- Orders: bounded priority/SLA, positive line quantity, one line per product, foreign-key ownership.
- Enumerated state checks constrain stored statuses. Services enforce legal transitions.

## Inventory lifecycle

Creation reserves stock. Delivery deducts quantity and releases the reservation atomically. Pre-dispatch cancellation releases the reservation without deducting stock. A failed delivery keeps its reservation and fleet assignment until RETURNED. A return after delivery restocks quantity once. Idempotency and state guards prevent duplicate deductions/restocks.

## Development and testing

`.env` is ignored. `.env.example` contains placeholders. Native local PostgreSQL and container PostgreSQL use separate data stores. Test classes create disposable schemas with an `ff_test_` UUID prefix inside the configured database and drop only that exact schema. CI supplies a PostgreSQL service, avoiding reliance on a particular developer's machine contents.

Run backups and restore drills before production use. No automatic production rollback or destructive down migration is supplied. Retention policies for audit/event/idempotency rows are a future operational requirement; deduplication records are currently retained indefinitely.
