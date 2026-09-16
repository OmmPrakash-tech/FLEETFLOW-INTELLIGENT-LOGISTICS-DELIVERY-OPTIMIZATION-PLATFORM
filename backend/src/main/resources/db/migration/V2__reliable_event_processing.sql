ALTER TABLE outbox_event ADD COLUMN processed_at TIMESTAMPTZ;
CREATE INDEX unprocessed_events ON outbox_event(id) WHERE processed_at IS NULL;
CREATE INDEX shipment_events_shipment ON shipment_event(shipment_id,id);
CREATE INDEX audit_created ON audit_log(created_at DESC);
CREATE INDEX auth_expiry ON auth_token(expires_at);
