# Architecture

FleetFlow is a modular monolith built on the existing `com.example.backend` Spring Boot entry point. A single PostgreSQL transaction can cover stock, order state, fleet capacity, audit records and events. This avoids distributed transactions for the core workflow.

## Modules

- `security`: JWT decoding, live account validation, roles, BCrypt, refresh rotation, reset delivery and request limiting.
- `common`: explicit SQL repository, application exceptions and error translation.
- `logistics`: validated request records, pure algorithms, transactional catalog/order/tracking services, API controllers, Redis distance cache and event processing.
- `configuration`: OpenAPI and opt-in demo seeding.
- `frontend`: memory-only sessions, API client, TanStack Query server state, role-specific routes and operations screens. Chart code is loaded separately.

Controllers obtain an `Actor` from Spring Security and delegate writes to transactional services. Service authorization remains active even when a route is called directly. SQL values use bound parameters. User-controlled sorting maps to a fixed allowlist.

## Order creation

1. Authenticate and validate the request and retry key.
2. Serialize duplicate requests with a PostgreSQL transaction advisory lock.
3. Insert the order and snapshot product prices.
4. Score candidate warehouses, lock warehouse rows in a stable order, then verify stock under inventory row locks.
5. Reserve every item and increment the warehouse fulfillment load.
6. Record confirmed, allocating and assigned states, audit actions and durable events.
7. Commit everything together; failures roll back the whole operation.

## Events

The `outbox_event` table is written in the same transaction as the order. A scheduled consumer selects up to 100 unprocessed events with `FOR UPDATE SKIP LOCKED`, creates capacity alerts and marks the events processed within a transaction. If it fails, PostgreSQL rolls back both effects and the processed marker. Alerts have a partial unique index for active resource/type pairs. The scheduler also checks late shipments and removes expired auth tokens.

This is a database-backed internal event system. No Kafka/RabbitMQ broker or external delivery guarantee is claimed. A future publisher can consume unprocessed outbox rows; external consumers will need their own deduplication keys and delivery acknowledgments.

## Tracking

REST snapshots and authenticated SSE return the same authorized shipment projection. SSE checks persisted state every three seconds and emits only when state changes, with heartbeat comments otherwise. A one-minute connection lifetime prompts the frontend to reconnect. A version/active check stops revoked sessions; resource access is checked on every snapshot. No client timer fabricates movement.

## Scaling boundary

Database transactions are safe across multiple app instances. The current warehouse allocator conservatively locks all candidate warehouses; the driver allocator may temporarily reject work when candidates are locked by another assignment. These choices favor correctness over throughput. SSE connection limits and the local rate limiter must be revisited before horizontal scale.
