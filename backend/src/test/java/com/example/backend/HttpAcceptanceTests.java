package com.example.backend;

import static org.junit.jupiter.api.Assertions.*;

import com.example.backend.common.Store;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
class HttpAcceptanceTests extends PostgresTestDatabase {
  @Value("${local.server.port}")
  int port;

  @Autowired Store db;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final JsonMapper json = JsonMapper.builder().build();

  private record Reply(int status, Map<String, Object> data) {}

  Reply request(String method, String path, String token, Object body) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", UUID.randomUUID().toString());
    if (token != null) builder.header("Authorization", "Bearer " + token);
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    Map<String, Object> data =
        response.body().isBlank() ? Map.of() : json.readValue(response.body(), Map.class);
    return new Reply(response.statusCode(), data);
  }

  String create(String path, String token, Object body) throws Exception {
    var r = request("POST", path, token, body);
    assertEquals(200, r.status, r.data.toString());
    return r.data.get("id").toString();
  }

  String register(String email) throws Exception {
    var reply =
        request(
            "POST",
            "/api/auth/register",
            null,
            Map.of("name", "HTTP test", "email", email, "password", "http-acceptance-password"));
    assertEquals(200, reply.status, reply.data.toString());
    return reply.data.get("accessToken").toString();
  }

  @Test
  void customerToDeliveryWorkflowOverHttp() throws Exception {
    String suffix = UUID.randomUUID().toString();
    String email = "admin-" + suffix + "@test.invalid";
    register(email);
    db.update("UPDATE app_user SET role='ADMIN' WHERE email=?", email);
    var login =
        request(
            "POST",
            "/api/auth/login",
            null,
            Map.of("email", email, "password", "http-acceptance-password"));
    assertEquals(200, login.status);
    String admin = login.data.get("accessToken").toString();
    String customer = register("customer-" + suffix + "@test.invalid"),
        stranger = register("stranger-" + suffix + "@test.invalid");
    assertEquals(401, request("GET", "/api/auth/me", null, null).status);
    assertEquals(
        403,
        request(
                "POST",
                "/api/products",
                customer,
                Map.of("sku", "NO", "name", "Forbidden", "weightKg", 1, "price", 10))
            .status);
    String warehouse =
        create(
            "/api/warehouses",
            admin,
            Map.of(
                "name",
                "HTTP Depot " + suffix,
                "address",
                "Test warehouse",
                "latitude",
                20,
                "longitude",
                85,
                "capacity",
                20,
                "status",
                "ACTIVE"));
    String product =
        create(
            "/api/products",
            admin,
            Map.of("sku", suffix, "name", "HTTP Product", "weightKg", 2, "price", 100));
    assertEquals(
        200,
        request(
                "POST",
                "/api/inventory/stock",
                admin,
                Map.of("warehouseId", warehouse, "productId", product, "quantity", 10))
            .status);
    String vehicle =
        create(
            "/api/vehicles",
            admin,
            Map.of("registrationNumber", suffix, "type", "VAN", "capacityKg", 100));
    String driver =
        create(
            "/api/drivers",
            admin,
            Map.of("name", "HTTP Driver", "vehicleId", vehicle, "latitude", 20, "longitude", 85));
    var orderBody =
        Map.of(
            "address",
            "Customer address",
            "latitude",
            20.1,
            "longitude",
            85.1,
            "priority",
            2,
            "slaHours",
            24,
            "items",
            List.of(Map.of("productId", product, "quantity", 2)));
    String order = create("/api/orders", customer, orderBody);
    var malformed = new HashMap<String, Object>(orderBody);
    malformed.put("items", List.of(Map.of("productId", product, "quantity", 1.5)));
    assertEquals(400, request("POST", "/api/orders", customer, malformed).status);
    assertEquals(403, request("GET", "/api/orders/" + order, stranger, null).status);
    assertEquals(
        409,
        request(
                "POST",
                "/api/orders/" + order + "/transition",
                admin,
                Map.of("status", "DELIVERED"))
            .status);
    assertEquals(
        403,
        request(
                "POST",
                "/api/orders/" + order + "/transition",
                customer,
                Map.of("status", "PICKING"))
            .status);
    for (String state : List.of("PICKING", "PACKED"))
      assertEquals(
          200,
          request("POST", "/api/orders/" + order + "/transition", admin, Map.of("status", state))
              .status);
    String shipment = create("/api/orders/" + order + "/assign", admin, null);
    assertEquals(403, request("GET", "/api/tracking/" + shipment, stranger, null).status);
    assertEquals(
        403,
        request(
                "POST",
                "/api/tracking/drivers/" + driver + "/position",
                customer,
                Map.of("latitude", 20.05, "longitude", 85.05))
            .status);
    assertEquals(
        200,
        request(
                "POST",
                "/api/tracking/drivers/" + driver + "/position",
                admin,
                Map.of("latitude", 20.05, "longitude", 85.05))
            .status);
    var updated = request("GET", "/api/tracking/" + shipment, customer, null);
    assertEquals(20.05, ((Number) updated.data.get("latitude")).doubleValue());
    assertNotNull(updated.data.get("eta"));
    var streamRequest =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/tracking/" + shipment + "/stream"))
            .header("Authorization", "Bearer " + customer)
            .timeout(Duration.ofSeconds(10))
            .build();
    try (var responseBody =
            http.send(streamRequest, HttpResponse.BodyHandlers.ofInputStream()).body();
        var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var event =
          pool.submit(() -> new BufferedReader(new InputStreamReader(responseBody)).readLine());
      assertEquals("event:tracking", event.get(10, TimeUnit.SECONDS));
    }
    for (String state : List.of("DISPATCHED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED")) {
      var result =
          request("POST", "/api/orders/" + order + "/transition", admin, Map.of("status", state));
      assertEquals(200, result.status, result.data.toString());
    }
    var tracking = request("GET", "/api/tracking/" + shipment, customer, null);
    assertEquals("DELIVERED", tracking.data.get("status"));
    assertNull(
        tracking.data.get("latitude"),
        "Completed shipments must not expose the driver's subsequent position");
    var stock =
        db.one(
            "SELECT quantity,reserved FROM inventory WHERE warehouse_id=? AND product_id=?",
            UUID.fromString(warehouse),
            UUID.fromString(product));
    assertEquals(8, Store.integer(stock, "quantity"));
    assertEquals(0, Store.integer(stock, "reserved"));
    assertEquals(200, request("GET", "/api/analytics", customer, null).status);
    assertEquals(200, request("GET", "/v3/api-docs", null, null).status);
    assertEquals(200, request("POST", "/api/auth/logout", customer, null).status);
    assertEquals(401, request("GET", "/api/auth/me", customer, null).status);
  }
}
