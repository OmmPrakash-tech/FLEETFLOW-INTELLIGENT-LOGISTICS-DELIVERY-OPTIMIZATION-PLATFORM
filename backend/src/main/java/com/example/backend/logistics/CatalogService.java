package com.example.backend.logistics;

import com.example.backend.common.*;
import com.example.backend.security.Actor;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {
  private final Store db;
  private final Idempotency idem;

  public CatalogService(Store db, Idempotency idem) {
    this.db = db;
    this.idem = idem;
  }

  @Transactional
  public UUID product(Actor actor, Requests.Product r) {
    actor.requireStaff();
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO product(id,sku,name,weight_kg,price) VALUES(?,?,?,?,?)",
        id,
        r.sku(),
        r.name(),
        r.weightKg(),
        r.price());
    db.audit(actor.id(), "PRODUCT_CREATED", id);
    return id;
  }

  @Transactional
  public UUID warehouse(Actor actor, Requests.Warehouse r) {
    actor.requireStaff();
    new Optimization.Point(r.latitude(), r.longitude());
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO warehouse(id,name,address,latitude,longitude,capacity,status) VALUES(?,?,?,?,?,?,?)",
        id,
        r.name(),
        r.address(),
        r.latitude(),
        r.longitude(),
        r.capacity(),
        r.status().name());
    db.audit(actor.id(), "WAREHOUSE_CREATED", id);
    return id;
  }

  @Transactional
  public void stock(Actor actor, Requests.Stock r, String key) {
    actor.requireStaff();
    String payload = r.toString();
    if (idem.existing(actor.id(), key, "stock", payload) != null) return;
    db.one("SELECT id FROM warehouse WHERE id=? FOR UPDATE", r.warehouseId());
    db.update(
        "INSERT INTO inventory(warehouse_id,product_id,quantity) VALUES(?,?,?) ON CONFLICT(warehouse_id,product_id) DO UPDATE SET quantity=inventory.quantity+EXCLUDED.quantity",
        r.warehouseId(),
        r.productId(),
        r.quantity());
    db.update(
        "UPDATE alert SET resolved=true WHERE type='LOW_INVENTORY' AND resource_id=? AND EXISTS(SELECT 1 FROM inventory WHERE warehouse_id=? AND product_id=? AND quantity-reserved>low_stock_threshold)",
        r.warehouseId() + ":" + r.productId(),
        r.warehouseId(),
        r.productId());
    idem.save(actor.id(), key, "stock", payload, r.warehouseId());
    db.audit(actor.id(), "STOCK_IN", r.warehouseId());
  }

  @Transactional
  public UUID vehicle(Actor actor, Requests.Vehicle r) {
    actor.requireStaff();
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO vehicle(id,registration_number,type,capacity_kg,status) VALUES(?,?,?,?,'AVAILABLE')",
        id,
        r.registrationNumber(),
        r.type().name(),
        r.capacityKg());
    db.audit(actor.id(), "VEHICLE_CREATED", id);
    return id;
  }

  @Transactional
  public UUID driver(Actor actor, Requests.Driver r) {
    actor.requireStaff();
    new Optimization.Point(r.latitude(), r.longitude());
    if (r.userId() != null
        && !db.one("SELECT role FROM app_user WHERE id=?", r.userId()).get("role").equals("DRIVER"))
      throw ApiException.conflict("INVALID_DRIVER_ACCOUNT", "User must have DRIVER role");
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO driver(id,user_id,name,vehicle_id,latitude,longitude,status) VALUES(?,?,?,?,?,?,'AVAILABLE')",
        id,
        r.userId(),
        r.name(),
        r.vehicleId(),
        r.latitude(),
        r.longitude());
    db.audit(actor.id(), "DRIVER_CREATED", id);
    return id;
  }

  @Transactional
  public void account(Actor actor, UUID id, Requests.Account r) {
    actor.requireAdmin();
    if (actor.id().equals(id))
      throw ApiException.conflict(
          "SELF_MANAGEMENT", "Use another administrator to change this account");
    db.one("SELECT id FROM app_user WHERE id=? FOR UPDATE", id);
    if (r.role() != Requests.Role.DRIVER
        && !db.rows("SELECT id FROM driver WHERE user_id=?", id).isEmpty())
      throw ApiException.conflict(
          "DRIVER_ACCOUNT", "Linked driver accounts must retain their role");
    db.update(
        "UPDATE app_user SET role=?,active=?,force_reset=?,token_version=token_version+1 WHERE id=?",
        r.role().name(),
        r.active(),
        r.forceReset(),
        id);
    db.update("DELETE FROM auth_token WHERE user_id=?", id);
    db.audit(actor.id(), "ACCOUNT_UPDATED", id);
  }

  @Transactional
  public UUID address(Actor actor, Requests.Address r) {
    UUID id = UUID.randomUUID();
    new Optimization.Point(r.latitude(), r.longitude());
    db.update(
        "INSERT INTO address(id,user_id,label,address,latitude,longitude) VALUES(?,?,?,?,?,?)",
        id,
        actor.id(),
        r.label(),
        r.address(),
        r.latitude(),
        r.longitude());
    return id;
  }

  @Transactional
  public void updateWarehouse(Actor actor, UUID id, Requests.Warehouse r) {
    actor.requireStaff();
    new Optimization.Point(r.latitude(), r.longitude());
    var w = db.one("SELECT * FROM warehouse WHERE id=? FOR UPDATE", id);
    if (r.capacity() < Store.integer(w, "current_load"))
      throw ApiException.conflict(
          "CAPACITY_IN_USE", "Capacity cannot be less than current workload");
    db.update(
        "UPDATE warehouse SET name=?,address=?,latitude=?,longitude=?,capacity=?,status=? WHERE id=?",
        r.name(),
        r.address(),
        r.latitude(),
        r.longitude(),
        r.capacity(),
        r.status().name(),
        id);
    db.audit(actor.id(), "WAREHOUSE_UPDATED", id);
  }

  @Transactional
  public void driverAvailability(Actor actor, UUID id, Requests.DriverStatus status) {
    var d = db.one("SELECT * FROM driver WHERE id=? FOR UPDATE", id);
    if (!actor.staff() && !actor.id().equals(d.get("user_id"))) throw ApiException.forbidden();
    if (!Set.of(
                Requests.DriverStatus.AVAILABLE,
                Requests.DriverStatus.OFFLINE,
                Requests.DriverStatus.ON_LEAVE)
            .contains(status)
        || Store.integer(d, "workload") != 0)
      throw ApiException.conflict(
          "DRIVER_COMMITTED", "Availability can only change for an unassigned driver");
    db.update("UPDATE driver SET status=?,updated_at=now() WHERE id=?", status.name(), id);
    db.audit(actor.id(), "DRIVER_AVAILABILITY_UPDATED", id);
  }

  @Transactional
  public void vehicleAvailability(Actor actor, UUID id, Requests.VehicleStatus status) {
    actor.requireStaff();
    var v = db.one("SELECT * FROM vehicle WHERE id=? FOR UPDATE", id);
    if (v.get("status").equals("ASSIGNED") || status == Requests.VehicleStatus.ASSIGNED)
      throw ApiException.conflict(
          "VEHICLE_COMMITTED", "Assigned vehicles are managed by shipment lifecycle");
    db.update("UPDATE vehicle SET status=? WHERE id=?", status.name(), id);
    db.audit(actor.id(), "VEHICLE_AVAILABILITY_UPDATED", id);
  }
}
