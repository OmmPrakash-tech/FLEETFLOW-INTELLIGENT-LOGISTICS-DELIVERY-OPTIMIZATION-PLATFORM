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

  private static final Map<String, Set<Integer>> ENDPOINTS = new TreeMap<>();

  @org.junit.jupiter.api.AfterAll
  static void endpointReport() throws Exception {
    java.nio.file.Files.writeString(
        java.nio.file.Path.of("target", "api-verification.txt"),
        "Distinct method/path pairs: "
            + ENDPOINTS.size()
            + "\n"
            + ENDPOINTS.entrySet().stream()
                .map(e -> e.getKey() + " " + e.getValue())
                .collect(java.util.stream.Collectors.joining("\n")));
  }

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
    String normalized =
        path.split("\\?")[0].replaceAll(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "{id}");
    ENDPOINTS
        .computeIfAbsent(method + " " + normalized, k -> new TreeSet<>())
        .add(response.statusCode());
    Object parsed =
        response.body().isBlank() ? Map.of() : json.readValue(response.body(), Object.class);
    Map<String, Object> data =
        parsed instanceof Map ? (Map<String, Object>) parsed : Map.of("rows", parsed);
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
  void signedTokensWithInvalidVersionClaimsAreRejected() throws Exception {
    String email = "claims-" + UUID.randomUUID() + "@test.invalid";
    register(email);
    String id = db.one("SELECT id FROM app_user WHERE email=?", email).get("id").toString();
    for (Object version : Arrays.asList(null, "zero", 0.5, -1)) {
      var claims =
          new com.nimbusds.jwt.JWTClaimsSet.Builder()
              .issuer("fleetflow")
              .subject(id)
              .expirationTime(new Date(System.currentTimeMillis() + 60000));
      if (version != null) claims.claim("version", version);
      var jwt =
          new com.nimbusds.jwt.SignedJWT(
              new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
              claims.build());
      jwt.sign(
          new com.nimbusds.jose.crypto.MACSigner(
              "test-only-key-with-more-than-thirty-two-characters"));
      var response = request("GET", "/api/auth/me", jwt.serialize(), null);
      assertEquals(401, response.status);
      assertEquals("INVALID_TOKEN", response.data.get("code"));
    }
  }

  @Test
  void malformedRoutesAndBoundaryErrorsAreSafeAndCorrelated() throws Exception {
    String email = "routes-" + UUID.randomUUID() + "@test.invalid";
    String token = register(email);
    db.update("UPDATE app_user SET role='ADMIN' WHERE email=?", email);
    var point = Map.of("latitude", 20, "longitude", 85);
    var valid =
        request(
            "POST",
            "/api/routes/plan",
            token,
            Map.of("start", point, "stops", List.of(point, point), "speedKmh", 35));
    assertEquals(200, valid.status);
    assertEquals(List.of(0, 1), valid.data.get("stopOrder"));
    assertEquals(10, ((Number) valid.data.get("durationMinutes")).intValue());
    for (Object stops :
        List.of(List.of(), Arrays.asList(point, null), Collections.nCopies(51, point)))
      assertEquals(
          400,
          request(
                  "POST",
                  "/api/routes/plan",
                  token,
                  Map.of("start", point, "stops", stops, "speedKmh", 35))
              .status);
    var shortest =
        request(
            "POST",
            "/api/routes/shortest-path",
            token,
            Map.of(
                "start",
                "A",
                "end",
                "B",
                "nodes",
                Map.of("A", List.of(Map.of("to", "B", "km", 3)), "B", List.of())));
    assertEquals(200, shortest.status);
    assertEquals(3.0, ((Number) shortest.data.get("distance")).doubleValue());
    assertEquals(
        400,
        request(
                "POST",
                "/api/routes/shortest-path",
                token,
                Map.of(
                    "start",
                    "A",
                    "end",
                    "A",
                    "nodes",
                    Map.of("A", List.of(), "B", List.of(Map.of("to", "missing", "km", 1)))))
            .status);
    var denied = request("GET", "/api/auth/me", null, null);
    assertEquals(401, denied.status);
    assertEquals("/api/auth/me", denied.data.get("path"));
    assertNotNull(UUID.fromString(denied.data.get("requestId").toString()));
    var huge = "x".repeat(262145);
    for (boolean chunked : List.of(false, true)) {
      var body =
          chunked
              ? HttpRequest.BodyPublishers.ofInputStream(
                  () -> new ByteArrayInputStream(huge.getBytes()))
              : HttpRequest.BodyPublishers.ofString(huge);
      var response =
          http.send(
              HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/routes/plan"))
                  .header("Authorization", "Bearer " + token)
                  .header("Content-Type", "application/json")
                  .POST(body)
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(413, response.statusCode());
      var error = json.readValue(response.body(), Map.class);
      assertEquals(
          response.headers().firstValue("X-Request-ID").orElseThrow(), error.get("requestId"));
      assertEquals("PAYLOAD_TOO_LARGE", error.get("code"));
    }
    assertEquals(
        401,
        request(
                "POST",
                "/api/auth/login",
                null,
                Map.of("email", email, "password", "incorrect-password"))
            .status);
    assertEquals(404, request("GET", "/api/orders/" + UUID.randomUUID(), token, null).status);
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
    String operatorEmail = "operator-" + suffix + "@test.invalid";
    String operator = register(operatorEmail);
    db.update("UPDATE app_user SET role='OPERATOR' WHERE email=?", operatorEmail);
    String operatorId =
        db.one("SELECT id FROM app_user WHERE email=?", operatorEmail).get("id").toString();
    assertEquals(403, request("GET", "/api/users", operator, null).status);
    assertEquals(
        403,
        request(
                "PATCH",
                "/api/users/" + operatorId,
                customer,
                Map.of("role", "ADMIN", "active", true, "forceReset", false))
            .status);
    assertEquals(
        200,
        request(
                "PATCH",
                "/api/users/" + operatorId,
                admin,
                Map.of("role", "OPERATOR", "active", true, "forceReset", false))
            .status);
    assertEquals(401, request("GET", "/api/auth/me", operator, null).status);
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
    String driverEmail = "driver-" + suffix + "@test.invalid";
    String driverToken = register(driverEmail);
    db.update("UPDATE app_user SET role='DRIVER' WHERE email=?", driverEmail);
    UUID driverUser = Store.id(db.one("SELECT id FROM app_user WHERE email=?", driverEmail), "id");
    db.update("UPDATE driver SET user_id=? WHERE id=?", driverUser, UUID.fromString(driver));
    assertEquals(
        200,
        request(
                "PATCH",
                "/api/drivers/" + driver + "/availability",
                driverToken,
                Map.of("status", "AVAILABLE"))
            .status);
    assertEquals(403, request("GET", "/api/analytics", driverToken, null).status);
    assertEquals(
        409,
        request(
                "PATCH",
                "/api/users/" + driverUser,
                admin,
                Map.of("role", "CUSTOMER", "active", true, "forceReset", false))
            .status);
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
    for (String path :
        List.of(
            "products",
            "warehouses",
            "inventory",
            "vehicles",
            "drivers",
            "orders",
            "shipments",
            "routes",
            "users",
            "addresses",
            "alerts",
            "audit")) assertEquals(200, request("GET", "/api/" + path, admin, null).status, path);
    assertEquals(403, request("GET", "/api/users", customer, null).status);
    assertEquals(403, request("GET", "/api/audit", customer, null).status);
    assertEquals(
        200,
        request(
                "PATCH",
                "/api/vehicles/" + vehicle + "/availability",
                admin,
                Map.of("status", "MAINTENANCE"))
            .status);
    assertEquals(
        200,
        request(
                "PATCH",
                "/api/vehicles/" + vehicle + "/availability",
                admin,
                Map.of("status", "AVAILABLE"))
            .status);
    assertEquals(
        200,
        request(
                "PATCH",
                "/api/drivers/" + driver + "/availability",
                admin,
                Map.of("status", "OFFLINE"))
            .status);
    assertEquals(
        200,
        request(
                "PATCH",
                "/api/drivers/" + driver + "/availability",
                admin,
                Map.of("status", "AVAILABLE"))
            .status);
    assertEquals(
        403,
        request(
                "PATCH",
                "/api/drivers/" + driver + "/availability",
                customer,
                Map.of("status", "OFFLINE"))
            .status);
    String addressId =
        create(
            "/api/addresses",
            customer,
            Map.of("label", "Home", "address", "HTTP address", "latitude", 20, "longitude", 85));
    assertEquals(404, request("DELETE", "/api/addresses/" + addressId, stranger, null).status);
    assertEquals(200, request("DELETE", "/api/addresses/" + addressId, customer, null).status);
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
    assertEquals(
        409,
        request(
                "PATCH",
                "/api/vehicles/" + vehicle + "/availability",
                admin,
                Map.of("status", "MAINTENANCE"))
            .status);
    assertEquals(
        409,
        request(
                "PATCH",
                "/api/drivers/" + driver + "/availability",
                driverToken,
                Map.of("status", "OFFLINE"))
            .status);
    assertEquals(200, request("GET", "/api/orders/" + order, driverToken, null).status);
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
      ENDPOINTS.computeIfAbsent("GET /api/tracking/{id}/stream", k -> new TreeSet<>()).add(200);
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
    assertEquals(
        404, request("POST", "/api/alerts/9223372036854775807/resolve", admin, null).status);
    assertEquals(404, request("GET", "/api/unknown-resource", admin, null).status);
    assertEquals(405, request("DELETE", "/api/routes/plan", admin, null).status);
    assertEquals(200, request("GET", "/v3/api-docs", null, null).status);
    assertEquals(200, request("POST", "/api/auth/logout", customer, null).status);
    assertEquals(401, request("GET", "/api/auth/me", customer, null).status);
  }
}
