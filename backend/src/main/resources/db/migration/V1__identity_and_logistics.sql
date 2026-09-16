CREATE TABLE app_user (
 id UUID PRIMARY KEY, email VARCHAR(254) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL,
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(20) NOT NULL CHECK(role IN ('ADMIN','OPERATOR','DRIVER','CUSTOMER')),
 active BOOLEAN NOT NULL DEFAULT TRUE, force_reset BOOLEAN NOT NULL DEFAULT FALSE,
 token_version INT NOT NULL DEFAULT 0, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE auth_token (
 token_hash VARCHAR(64) PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id),
 kind VARCHAR(10) NOT NULL CHECK(kind IN ('REFRESH','RESET')), expires_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE address (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES app_user(id), label VARCHAR(80) NOT NULL,
 address VARCHAR(500) NOT NULL, latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180)
);
CREATE TABLE product (
 id UUID PRIMARY KEY, sku VARCHAR(60) NOT NULL UNIQUE, name VARCHAR(160) NOT NULL,
 weight_kg NUMERIC(12,3) NOT NULL CHECK(weight_kg > 0), price NUMERIC(12,2) NOT NULL CHECK(price >= 0)
);
CREATE TABLE warehouse (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL UNIQUE, address VARCHAR(500) NOT NULL,
 latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 capacity INT NOT NULL CHECK(capacity > 0), current_load INT NOT NULL DEFAULT 0 CHECK(current_load >= 0 AND current_load <= capacity),
 status VARCHAR(20) NOT NULL CHECK(status IN ('ACTIVE','BUSY','FULL','MAINTENANCE','INACTIVE'))
);
CREATE TABLE inventory (
 warehouse_id UUID REFERENCES warehouse(id), product_id UUID REFERENCES product(id),
 quantity INT NOT NULL CHECK(quantity >= 0), reserved INT NOT NULL DEFAULT 0 CHECK(reserved >= 0 AND reserved <= quantity),
 low_stock_threshold INT NOT NULL DEFAULT 10 CHECK(low_stock_threshold >= 0), PRIMARY KEY(warehouse_id, product_id)
);
CREATE TABLE vehicle (
 id UUID PRIMARY KEY, registration_number VARCHAR(40) NOT NULL UNIQUE,
 type VARCHAR(10) NOT NULL CHECK(type IN ('BIKE','VAN','TRUCK')),
 capacity_kg NUMERIC(12,3) NOT NULL CHECK(capacity_kg > 0), status VARCHAR(20) NOT NULL CHECK(status IN ('AVAILABLE','ASSIGNED','MAINTENANCE','INACTIVE'))
);
CREATE TABLE driver (
 id UUID PRIMARY KEY, user_id UUID UNIQUE REFERENCES app_user(id), name VARCHAR(120) NOT NULL,
 vehicle_id UUID UNIQUE REFERENCES vehicle(id), latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180),
 status VARCHAR(20) NOT NULL CHECK(status IN ('AVAILABLE','ASSIGNED','ON_DELIVERY','OFFLINE','ON_LEAVE')),
 workload INT NOT NULL DEFAULT 0 CHECK(workload BETWEEN 0 AND 1), updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE customer_order (
 id UUID PRIMARY KEY, customer_id UUID NOT NULL REFERENCES app_user(id), warehouse_id UUID REFERENCES warehouse(id),
 status VARCHAR(30) NOT NULL CHECK(status IN ('CREATED','CONFIRMED','ALLOCATING','WAREHOUSE_ASSIGNED','PICKING','PACKED','DISPATCHED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','CANCELLED','FAILED','RETURN_REQUESTED','RETURNED')),
 address VARCHAR(500) NOT NULL, latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
 longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180), priority INT NOT NULL CHECK(priority BETWEEN 1 AND 3),
 sla_hours INT NOT NULL CHECK(sla_hours BETWEEN 1 AND 168), total NUMERIC(14,2) NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), delivered_at TIMESTAMPTZ
);
CREATE INDEX orders_customer_created ON customer_order(customer_id, created_at DESC);
CREATE INDEX orders_status ON customer_order(status);
CREATE TABLE order_item (
 order_id UUID REFERENCES customer_order(id), product_id UUID REFERENCES product(id),
 quantity INT NOT NULL CHECK(quantity > 0), unit_price NUMERIC(12,2) NOT NULL,
 PRIMARY KEY(order_id,product_id)
);
CREATE TABLE route (
 id UUID PRIMARY KEY, nodes_json TEXT NOT NULL, distance_km DOUBLE PRECISION NOT NULL CHECK(distance_km >= 0),
 duration_minutes INT NOT NULL CHECK(duration_minutes >= 0), source VARCHAR(30) NOT NULL DEFAULT 'GEODESIC_ESTIMATE'
);
CREATE TABLE shipment (
 id UUID PRIMARY KEY, order_id UUID NOT NULL UNIQUE REFERENCES customer_order(id), warehouse_id UUID NOT NULL REFERENCES warehouse(id),
 driver_id UUID REFERENCES driver(id), route_id UUID REFERENCES route(id),
 status VARCHAR(30) NOT NULL CHECK(status IN ('CREATED','READY_FOR_DISPATCH','DISPATCHED','IN_TRANSIT','OUT_FOR_DELIVERY','DELIVERED','FAILED','RETURNED')),
 eta TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), delivered_at TIMESTAMPTZ
);
CREATE INDEX shipments_status_eta ON shipment(status,eta);
CREATE TABLE shipment_event (
 id BIGSERIAL PRIMARY KEY, shipment_id UUID NOT NULL REFERENCES shipment(id), status VARCHAR(30) NOT NULL,
 detail VARCHAR(500) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE idempotency (
 actor_id UUID NOT NULL REFERENCES app_user(id), request_key VARCHAR(100) NOT NULL,
 operation VARCHAR(100) NOT NULL, fingerprint VARCHAR(64) NOT NULL, resource_id UUID NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY(actor_id,request_key,operation)
);
CREATE TABLE audit_log (
 id BIGSERIAL PRIMARY KEY, actor_id UUID REFERENCES app_user(id), action VARCHAR(80) NOT NULL,
 resource_id VARCHAR(100) NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE alert (
 id BIGSERIAL PRIMARY KEY, type VARCHAR(40) NOT NULL, resource_id VARCHAR(100) NOT NULL,
 message VARCHAR(500) NOT NULL, resolved BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX active_alert ON alert(type,resource_id) WHERE NOT resolved;
CREATE TABLE outbox_event (
 id BIGSERIAL PRIMARY KEY, aggregate_id UUID NOT NULL, type VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
