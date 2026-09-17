-- Index actual ownership, token revocation and tracking lookups. Existing migrations stay immutable.
CREATE INDEX auth_token_user ON auth_token(user_id);
CREATE INDEX address_user ON address(user_id);
CREATE INDEX shipment_driver_status ON shipment(driver_id, status);
CREATE INDEX orders_warehouse ON customer_order(warehouse_id);
CREATE INDEX order_item_product ON order_item(product_id);
CREATE INDEX inventory_product ON inventory(product_id);
