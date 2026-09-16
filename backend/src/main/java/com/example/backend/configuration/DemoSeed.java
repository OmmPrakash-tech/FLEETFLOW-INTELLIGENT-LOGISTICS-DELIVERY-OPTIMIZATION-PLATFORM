package com.example.backend.configuration;

import com.example.backend.common.Store;
import com.example.backend.logistics.*;
import com.example.backend.security.Actor;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opt-in persisted demo data. No default password and no production seeding. */
@Component
public class DemoSeed implements ApplicationRunner {
  private final Store db;
  private final CatalogService catalog;
  private final OrderService orders;
  private final PasswordEncoder passwords;
  private final boolean enabled;
  private final String password;

  public DemoSeed(
      Store db,
      CatalogService catalog,
      OrderService orders,
      PasswordEncoder passwords,
      @Value("${fleetflow.demo-enabled}") boolean enabled,
      @Value("${fleetflow.demo-password}") String password) {
    this.db = db;
    this.catalog = catalog;
    this.orders = orders;
    this.passwords = passwords;
    this.enabled = enabled;
    this.password = password;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    if (password.length() < 12 || password.length() > 72)
      throw new IllegalStateException("Set a DEMO_PASSWORD between 12 and 72 characters");
    db.rows("SELECT pg_advisory_xact_lock(75120834)");
    if (!db.rows("SELECT id FROM app_user WHERE email='admin@fleetflow.demo'").isEmpty()) return;
    UUID admin = user("admin", "ADMIN"),
        customer = user("customer", "CUSTOMER"),
        driverUser = user("driver", "DRIVER");
    user("operator", "OPERATOR");
    var actor = new Actor(admin, "ADMIN");
    var shopper = new Actor(customer, "CUSTOMER");
    UUID north =
        catalog.warehouse(
            actor,
            new Requests.Warehouse(
                "Bhubaneswar Central",
                "Mancheswar Industrial Estate, Bhubaneswar",
                20.326,
                85.839,
                120,
                Requests.WarehouseStatus.ACTIVE));
    UUID south =
        catalog.warehouse(
            actor,
            new Requests.Warehouse(
                "Cuttack Distribution",
                "Jagatpur, Cuttack",
                20.502,
                85.89,
                80,
                Requests.WarehouseStatus.ACTIVE));
    UUID electronics =
        catalog.product(
            actor,
            new Requests.Product(
                "FF-ELEC-01", "Electronics parcel", new BigDecimal("2.5"), new BigDecimal("1499")));
    UUID essentials =
        catalog.product(
            actor,
            new Requests.Product(
                "FF-HOME-02", "Home essentials box", new BigDecimal("5"), new BigDecimal("799")));
    UUID medical =
        catalog.product(
            actor,
            new Requests.Product(
                "FF-CARE-03", "Care supply kit", new BigDecimal("1"), new BigDecimal("499")));
    for (UUID warehouse : List.of(north, south))
      for (UUID product : List.of(electronics, essentials, medical))
        catalog.stock(
            actor, new Requests.Stock(warehouse, product, 80), UUID.randomUUID().toString());
    for (int i = 0; i < 4; i++) {
      UUID vehicle =
          catalog.vehicle(
              actor,
              new Requests.Vehicle(
                  "DEMO-OD-" + (100 + i),
                  i == 0 ? Requests.VehicleType.BIKE : Requests.VehicleType.VAN,
                  BigDecimal.valueOf(i == 0 ? 20 : 500)));
      catalog.driver(
          actor,
          new Requests.Driver(
              List.of("Arjun Das", "Meera Sahu", "Ravi Patel", "Ananya Sen").get(i),
              i == 0 ? driverUser : null,
              vehicle,
              20.30 + i * .02,
              85.83 + i * .01));
    }
    for (int i = 0; i < 6; i++) {
      UUID order =
          orders.create(
              shopper,
              new Requests.Order(
                  List.of(
                          "Saheed Nagar, Bhubaneswar",
                          "Patia, Bhubaneswar",
                          "College Square, Cuttack")
                      .get(i % 3),
                  20.29 + i * .015,
                  85.84 + i * .004,
                  i % 3 + 1,
                  24,
                  List.of(new Requests.Item(i % 2 == 0 ? electronics : essentials, 1 + i % 2))),
              "demo-order-" + i);
      if (i < 4) {
        orders.transition(actor, order, OrderState.PICKING, "demo-picking-" + i);
        orders.transition(actor, order, OrderState.PACKED, "demo-packed-" + i);
        orders.assign(actor, order, "demo-assign-" + i);
        orders.transition(actor, order, OrderState.DISPATCHED, "demo-dispatch-" + i);
      }
      if (i < 3) orders.transition(actor, order, OrderState.IN_TRANSIT, "demo-transit-" + i);
      if (i < 2) {
        orders.transition(actor, order, OrderState.OUT_FOR_DELIVERY, "demo-out-" + i);
        orders.transition(actor, order, OrderState.DELIVERED, "demo-deliver-" + i);
      }
    }
  }

  private UUID user(String name, String role) {
    UUID id = UUID.randomUUID();
    db.update(
        "INSERT INTO app_user(id,email,name,password_hash,role) VALUES(?,?,?,?,?)",
        id,
        name + "@fleetflow.demo",
        "Demo " + name,
        passwords.encode(password),
        role);
    return id;
  }
}
