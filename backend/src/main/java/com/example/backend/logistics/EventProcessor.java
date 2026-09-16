package com.example.backend.logistics;

import com.example.backend.common.Store;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Database-backed internal event consumer. Rollback leaves events available for retry. */
@Component
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "fleetflow.events-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class EventProcessor {
  private final Store db;

  public EventProcessor(Store db) {
    this.db = db;
  }

  @Scheduled(fixedDelayString = "${fleetflow.events-delay-ms:15000}", initialDelay = 15000)
  @Transactional
  public void process() {
    var events =
        db.rows(
            "SELECT id,aggregate_id,type FROM outbox_event WHERE processed_at IS NULL ORDER BY id LIMIT 100 FOR UPDATE SKIP LOCKED");
    for (var event : events) {
      var warehouses =
          db.rows(
              "SELECT w.* FROM warehouse w JOIN customer_order o ON o.warehouse_id=w.id WHERE o.id=?",
              event.get("aggregate_id"));
      for (var warehouse : warehouses)
        if (Store.number(warehouse, "current_load") / Store.number(warehouse, "capacity") >= .85)
          db.alert("WAREHOUSE_CAPACITY", warehouse.get("id"), "Warehouse is at least 85% utilized");
      db.update("UPDATE outbox_event SET processed_at=now() WHERE id=?", event.get("id"));
    }
    for (var shipment :
        db.rows(
            "SELECT id FROM shipment WHERE eta<now() AND status IN ('DISPATCHED','IN_TRANSIT','OUT_FOR_DELIVERY')"))
      db.alert(
          "SHIPMENT_DELAY",
          shipment.get("id"),
          "Shipment has exceeded its latest estimated arrival");
    db.update("DELETE FROM auth_token WHERE expires_at<now()");
  }
}
