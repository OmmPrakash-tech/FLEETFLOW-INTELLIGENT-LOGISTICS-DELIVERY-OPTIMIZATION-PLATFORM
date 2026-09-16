package com.example.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@org.springframework.test.annotation.DirtiesContext
class BackendApplicationTests extends PostgresTestDatabase {

  @Test
  void contextLoads() {}
}
