package com.example.backend;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real PostgreSQL with a unique disposable schema; never truncates the developer's tables. */
abstract class PostgresTestDatabase {
  static final String SCHEMA = "ff_test_" + UUID.randomUUID().toString().replace("-", "");
  static final String URL =
      "jdbc:postgresql://"
          + System.getenv().getOrDefault("DB_HOST", "localhost")
          + ":"
          + System.getenv().getOrDefault("DB_PORT", "5432")
          + "/"
          + System.getenv().getOrDefault("DB_NAME", "fleetflow");

  static Connection connect() throws SQLException {
    return DriverManager.getConnection(
        URL, System.getenv().getOrDefault("DB_USERNAME", "postgres"), System.getenv("DB_PASSWORD"));
  }

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) throws SQLException {
    try (var connection = connect();
        var statement = connection.createStatement()) {
      statement.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
    }
    registry.add("spring.datasource.url", () -> URL + "?currentSchema=" + SCHEMA);
    registry.add("spring.flyway.default-schema", () -> SCHEMA);
    registry.add(
        "fleetflow.jwt-secret", () -> "test-only-key-with-more-than-thirty-two-characters");
    registry.add("fleetflow.demo-enabled", () -> false);
    registry.add("fleetflow.events-enabled", () -> false);
  }

  @AfterAll
  static void removeSchema() throws SQLException {
    if (!SCHEMA.matches("ff_test_[0-9a-f]{32}"))
      throw new IllegalStateException("Unsafe test schema");
    try (var connection = connect();
        var statement = connection.createStatement()) {
      statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }
  }
}
