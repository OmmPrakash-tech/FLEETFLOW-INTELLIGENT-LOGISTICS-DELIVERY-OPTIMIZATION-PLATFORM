package com.example.backend.logistics;

import com.example.backend.common.*;
import com.example.backend.security.Actor;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OperationsController {
  private final Store db;
  private final CatalogService catalog;
  private final OrderService orders;

  public OperationsController(Store db, CatalogService catalog, OrderService orders) {
    this.db = db;
    this.catalog = catalog;
    this.orders = orders;
  }

  private int offset(int page, int size) {
    if (page < 0 || page > 10000 || size < 1 || size > 100) throw new IllegalArgumentException();
    return page * size;
  }

  @GetMapping("/products")
  public Object products(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(defaultValue = "") String search) {
    return db.rows(
        "SELECT * FROM product WHERE name ILIKE ? OR sku ILIKE ? ORDER BY name,id LIMIT ? OFFSET ?",
        "%" + search + "%",
        "%" + search + "%",
        size,
        offset(page, size));
  }

  @PostMapping("/products")
  public Object product(@Valid @RequestBody Requests.Product r) {
    return Map.of("id", catalog.product(Actor.current(), r));
  }

  @GetMapping("/warehouses")
  public Object warehouses(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    Actor.current().requireStaff();
    return db.rows(
        "SELECT * FROM warehouse ORDER BY name,id LIMIT ? OFFSET ?", size, offset(page, size));
  }

  @PostMapping("/warehouses")
  public Object warehouse(@Valid @RequestBody Requests.Warehouse r) {
    return Map.of("id", catalog.warehouse(Actor.current(), r));
  }

  @PatchMapping("/warehouses/{id}")
  public void warehouseUpdate(@PathVariable UUID id, @Valid @RequestBody Requests.Warehouse r) {
    catalog.updateWarehouse(Actor.current(), id, r);
  }

  @GetMapping("/inventory")
  public Object inventory(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    Actor.current().requireStaff();
    return db.rows(
        "SELECT i.*,i.quantity-i.reserved AS available,p.name AS product_name,p.sku,w.name AS warehouse_name FROM inventory i JOIN product p ON p.id=i.product_id JOIN warehouse w ON w.id=i.warehouse_id ORDER BY w.name,p.name LIMIT ? OFFSET ?",
        size,
        offset(page, size));
  }

  @PostMapping("/inventory/stock")
  public Object stock(
      @Valid @RequestBody Requests.Stock r, @RequestHeader("Idempotency-Key") String key) {
    catalog.stock(Actor.current(), r, key);
    return Map.of("message", "Stock received");
  }

  @GetMapping("/vehicles")
  public Object vehicles(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    Actor.current().requireStaff();
    return db.rows(
        "SELECT * FROM vehicle ORDER BY registration_number LIMIT ? OFFSET ?",
        size,
        offset(page, size));
  }

  @PostMapping("/vehicles")
  public Object vehicle(@Valid @RequestBody Requests.Vehicle r) {
    return Map.of("id", catalog.vehicle(Actor.current(), r));
  }

  @PatchMapping("/vehicles/{id}/availability")
  public void vehicleAvailability(
      @PathVariable UUID id, @Valid @RequestBody Requests.VehicleAvailability r) {
    catalog.vehicleAvailability(Actor.current(), id, r.status());
  }

  @GetMapping("/drivers")
  public Object drivers(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    var actor = Actor.current();
    int skip = offset(page, size);
    if (actor.staff())
      return db.rows(
          "SELECT d.*,v.registration_number,v.capacity_kg,v.type FROM driver d JOIN vehicle v ON v.id=d.vehicle_id ORDER BY d.name LIMIT ? OFFSET ?",
          size,
          skip);
    return db.rows(
        "SELECT d.*,v.registration_number,v.capacity_kg,v.type FROM driver d JOIN vehicle v ON v.id=d.vehicle_id WHERE d.user_id=? LIMIT ? OFFSET ?",
        actor.id(),
        size,
        skip);
  }

  @PostMapping("/drivers")
  public Object driver(@Valid @RequestBody Requests.Driver r) {
    return Map.of("id", catalog.driver(Actor.current(), r));
  }

  @PatchMapping("/drivers/{id}/availability")
  public void driverAvailability(
      @PathVariable UUID id, @Valid @RequestBody Requests.DriverAvailability r) {
    catalog.driverAvailability(Actor.current(), id, r.status());
  }

  @GetMapping("/orders")
  public Object orders(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String search,
      @RequestParam(defaultValue = "newest") String sort) {
    var actor = Actor.current();
    int skip = offset(page, size);
    if (!status.isEmpty()) OrderState.valueOf(status);
    String ordering =
        switch (sort) {
          case "oldest" -> "o.created_at ASC";
          case "priority" -> "o.priority DESC,o.created_at";
          case "newest" -> "o.created_at DESC";
          default -> throw new IllegalArgumentException();
        };
    return db.rows(
        "SELECT o.*,w.name AS warehouse_name FROM customer_order o LEFT JOIN warehouse w ON w.id=o.warehouse_id WHERE (? OR o.customer_id=? OR EXISTS(SELECT 1 FROM shipment s JOIN driver d ON d.id=s.driver_id WHERE s.order_id=o.id AND d.user_id=?)) AND (?='' OR o.status=?) AND (o.address ILIKE ? OR CAST(o.id AS text) ILIKE ?) ORDER BY "
            + ordering
            + ",o.id LIMIT ? OFFSET ?",
        actor.staff(),
        actor.id(),
        actor.id(),
        status,
        status,
        "%" + search + "%",
        "%" + search + "%",
        size,
        skip);
  }

  @GetMapping("/orders/{id}")
  public Object order(@PathVariable UUID id) {
    var order = new LinkedHashMap<>(orders.accessible(Actor.current(), id, false));
    order.put(
        "items",
        db.rows(
            "SELECT i.*,p.name,p.sku FROM order_item i JOIN product p ON p.id=i.product_id WHERE i.order_id=?",
            id));
    order.put("shipments", db.rows("SELECT * FROM shipment WHERE order_id=?", id));
    return order;
  }

  @PostMapping("/orders")
  public Object create(
      @Valid @RequestBody Requests.Order r, @RequestHeader("Idempotency-Key") String key) {
    return Map.of("id", orders.create(Actor.current(), r, key));
  }

  @PostMapping("/orders/{id}/assign")
  public Object assign(@PathVariable UUID id, @RequestHeader("Idempotency-Key") String key) {
    return Map.of("id", orders.assign(Actor.current(), id, key));
  }

  @PostMapping("/orders/{id}/transition")
  public Object transition(
      @PathVariable UUID id,
      @Valid @RequestBody Requests.Transition r,
      @RequestHeader("Idempotency-Key") String key) {
    orders.transition(Actor.current(), id, r.status(), key);
    return Map.of("message", "Order updated");
  }

  @GetMapping("/shipments")
  public Object shipments(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    var actor = Actor.current();
    return db.rows(
        "SELECT s.*,d.name AS driver_name,o.address FROM shipment s JOIN customer_order o ON o.id=s.order_id LEFT JOIN driver d ON d.id=s.driver_id WHERE (? OR o.customer_id=? OR d.user_id=?) ORDER BY s.created_at DESC LIMIT ? OFFSET ?",
        actor.staff(),
        actor.id(),
        actor.id(),
        size,
        offset(page, size));
  }

  @GetMapping("/routes")
  public Object routes(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    var actor = Actor.current();
    return db.rows(
        "SELECT r.*,s.id AS shipment_id,o.address FROM route r JOIN shipment s ON s.route_id=r.id JOIN customer_order o ON o.id=s.order_id LEFT JOIN driver d ON d.id=s.driver_id WHERE (? OR o.customer_id=? OR d.user_id=?) ORDER BY s.created_at DESC LIMIT ? OFFSET ?",
        actor.staff(),
        actor.id(),
        actor.id(),
        size,
        offset(page, size));
  }

  @GetMapping("/users")
  public Object users(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    Actor.current().requireAdmin();
    return db.rows(
        "SELECT id,name,email,role,active,force_reset,created_at FROM app_user ORDER BY created_at DESC LIMIT ? OFFSET ?",
        size,
        offset(page, size));
  }

  @PatchMapping("/users/{id}")
  public Object account(@PathVariable UUID id, @Valid @RequestBody Requests.Account r) {
    catalog.account(Actor.current(), id, r);
    return Map.of("message", "Account updated");
  }

  @GetMapping("/addresses")
  public Object addresses() {
    return db.rows("SELECT * FROM address WHERE user_id=? ORDER BY label", Actor.current().id());
  }

  @PostMapping("/addresses")
  public Object address(@Valid @RequestBody Requests.Address r) {
    return Map.of("id", catalog.address(Actor.current(), r));
  }

  @DeleteMapping("/addresses/{id}")
  public void removeAddress(@PathVariable UUID id) {
    db.update("DELETE FROM address WHERE id=? AND user_id=?", id, Actor.current().id());
  }

  @GetMapping("/alerts")
  public Object alerts(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    Actor.current().requireStaff();
    return db.rows(
        "SELECT * FROM alert ORDER BY resolved,created_at DESC LIMIT ? OFFSET ?",
        size,
        offset(page, size));
  }

  @PostMapping("/alerts/{id}/resolve")
  public void resolve(@PathVariable long id) {
    Actor.current().requireStaff();
    db.update("UPDATE alert SET resolved=true WHERE id=?", id);
  }

  @GetMapping("/audit")
  public Object audit(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
    Actor.current().requireAdmin();
    return db.rows(
        "SELECT a.*,u.name AS actor_name FROM audit_log a LEFT JOIN app_user u ON u.id=a.actor_id ORDER BY a.id DESC LIMIT ? OFFSET ?",
        size,
        offset(page, size));
  }

  @GetMapping("/analytics")
  public Object analytics() {
    var actor = Actor.current();
    boolean all = actor.staff();
    if (actor.role().equals("DRIVER")) throw ApiException.forbidden();
    var result = new LinkedHashMap<String, Object>();
    result.put(
        "summary",
        db.one(
            "SELECT COUNT(*) AS orders,COUNT(*) FILTER(WHERE status='DELIVERED') AS delivered,COUNT(*) FILTER(WHERE status NOT IN ('DELIVERED','CANCELLED','RETURNED')) AS active,COALESCE(SUM(total),0) AS order_value,COALESCE(AVG(EXTRACT(EPOCH FROM (delivered_at-created_at))/3600) FILTER(WHERE delivered_at IS NOT NULL),0) AS average_delivery_hours,COUNT(*) FILTER(WHERE COALESCE(delivered_at,now())>created_at+sla_hours*interval '1 hour' AND status NOT IN ('CANCELLED','RETURNED')) AS delayed FROM customer_order WHERE (? OR customer_id=?)",
            all,
            actor.id()));
    result.put(
        "byStatus",
        db.rows(
            "SELECT status,COUNT(*) AS count FROM customer_order WHERE (? OR customer_id=?) GROUP BY status ORDER BY status",
            all,
            actor.id()));
    result.put(
        "trends",
        db.rows(
            "SELECT to_char(date_trunc('day',created_at),'YYYY-MM-DD') AS day,COUNT(*) AS orders,COUNT(*) FILTER(WHERE status='DELIVERED') AS delivered FROM customer_order WHERE (? OR customer_id=?) AND created_at>now()-interval '30 days' GROUP BY 1 ORDER BY 1",
            all,
            actor.id()));
    result.put(
        "deliveriesOverTime",
        db.rows(
            "SELECT to_char(date_trunc('day',delivered_at),'YYYY-MM-DD') AS day,COUNT(*) AS delivered FROM customer_order WHERE (? OR customer_id=?) AND delivered_at>now()-interval '30 days' GROUP BY 1 ORDER BY 1",
            all,
            actor.id()));
    result.put(
        "delayedDeliveryRate",
        db.one(
                "SELECT COALESCE(ROUND(100.0*COUNT(*) FILTER(WHERE delivered_at>created_at+sla_hours*interval '1 hour')/NULLIF(COUNT(*),0),2),0) AS percent FROM customer_order WHERE delivered_at IS NOT NULL AND (? OR customer_id=?)",
                all,
                actor.id())
            .get("percent"));
    if (all) {
      result.put(
          "warehouses",
          db.rows(
              "SELECT name,capacity,current_load,ROUND(current_load*100.0/capacity,1) AS utilization FROM warehouse ORDER BY name"));
      result.put("drivers", db.rows("SELECT status,COUNT(*) AS count FROM driver GROUP BY status"));
      result.put(
          "alerts",
          db.rows("SELECT * FROM alert WHERE NOT resolved ORDER BY created_at DESC LIMIT 5"));
    }
    return result;
  }
}
