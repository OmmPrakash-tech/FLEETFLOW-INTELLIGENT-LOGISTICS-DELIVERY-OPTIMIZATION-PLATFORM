package com.example.backend.logistics;

import com.example.backend.common.*;
import com.example.backend.security.Actor;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TrackingService {
  private final Store db;
  private final OrderService orders;

  public TrackingService(Store db, OrderService orders) {
    this.db = db;
    this.orders = orders;
  }

  public Map<String, Object> snapshot(Actor actor, UUID id) {
    var shipment =
        db.one(
            "SELECT s.*,r.nodes_json,r.distance_km,r.duration_minutes,r.source,d.name AS driver_name,d.latitude,d.longitude,d.updated_at AS position_updated_at,o.address FROM shipment s JOIN route r ON r.id=s.route_id JOIN driver d ON d.id=s.driver_id JOIN customer_order o ON o.id=s.order_id WHERE s.id=?",
            id);
    orders.accessible(actor, Store.id(shipment, "order_id"), false);
    if (!Set.of("READY_FOR_DISPATCH", "DISPATCHED", "IN_TRANSIT", "OUT_FOR_DELIVERY")
        .contains(shipment.get("status"))) {
      shipment.put("latitude", null);
      shipment.put("longitude", null);
      shipment.put("position_updated_at", null);
    }
    shipment.put(
        "events",
        db.rows(
            "SELECT id,status,detail,created_at FROM shipment_event WHERE shipment_id=? ORDER BY id",
            id));
    return shipment;
  }

  @Transactional
  public void position(Actor actor, UUID driverId, Requests.Position p) {
    new Optimization.Point(p.latitude(), p.longitude());
    var driver =
        db.one(
            "SELECT d.*,v.type FROM driver d JOIN vehicle v ON v.id=d.vehicle_id WHERE d.id=?",
            driverId);
    if (!actor.staff() && !actor.id().equals(driver.get("user_id"))) throw ApiException.forbidden();
    // Lock orders before drivers, consistent with status transitions and assignment.
    var active =
        db.rows(
            "SELECT o.id FROM customer_order o JOIN shipment s ON s.order_id=o.id WHERE s.driver_id=? AND s.status IN ('READY_FOR_DISPATCH','DISPATCHED','IN_TRANSIT','OUT_FOR_DELIVERY') ORDER BY o.id FOR UPDATE OF o",
            driverId);
    db.one("SELECT id FROM driver WHERE id=? FOR UPDATE", driverId);
    db.update(
        "UPDATE driver SET latitude=?,longitude=?,updated_at=now() WHERE id=?",
        p.latitude(),
        p.longitude(),
        driverId);
    for (var row : active) {
      var order = db.one("SELECT latitude,longitude FROM customer_order WHERE id=?", row.get("id"));
      int minutes =
          Optimization.etaMinutes(
              Optimization.distance(
                  new Optimization.Point(p.latitude(), p.longitude()), OrderService.point(order)),
              driver.get("type").equals("BIKE") ? 25 : 35,
              1,
              1);
      db.update(
          "UPDATE shipment SET eta=?,updated_at=now() WHERE order_id=?",
          java.sql.Timestamp.from(Instant.now().plusSeconds(minutes * 60L)),
          row.get("id"));
      db.event(Store.id(row, "id"), "DriverPositionUpdated");
    }
    db.audit(actor.id(), "DRIVER_POSITION_UPDATED", driverId);
  }
}
