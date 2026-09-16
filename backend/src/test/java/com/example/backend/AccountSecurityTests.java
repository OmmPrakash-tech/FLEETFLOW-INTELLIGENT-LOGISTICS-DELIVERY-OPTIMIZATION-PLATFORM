package com.example.backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.backend.common.*;
import com.example.backend.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@DirtiesContext
class AccountSecurityTests extends PostgresTestDatabase {
  @Autowired AuthService auth;
  @Autowired Store db;
  @MockitoBean PasswordResetDelivery delivery;

  @Test
  void resetTokensAreHashedAndSingleUse() {
    String email = UUID.randomUUID() + "@test.invalid";
    var session = auth.register("Recovery test", email, "original-test-password");
    auth.forgot(email);
    var token = ArgumentCaptor.forClass(String.class);
    verify(delivery).send(eq(email), token.capture());
    assertTrue(
        db.rows("SELECT token_hash FROM auth_token WHERE token_hash=?", token.getValue())
            .isEmpty());
    assertFalse(
        db.rows(
                "SELECT token_hash FROM auth_token WHERE token_hash=?",
                AuthService.hash(token.getValue()))
            .isEmpty());
    auth.reset(token.getValue(), "replacement-test-password");
    assertThrows(ApiException.class, () -> auth.reset(token.getValue(), "another-test-password"));
    assertThrows(ApiException.class, () -> auth.login(email, "original-test-password"));
    assertThrows(ApiException.class, () -> auth.refresh((String) session.get("refreshToken")));
    assertNotNull(auth.login(email, "replacement-test-password").get("accessToken"));
  }

  @Test
  void concurrentRefreshHasExactlyOneWinner() throws Exception {
    var session =
        auth.register("Refresh test", UUID.randomUUID() + "@test.invalid", "refresh-test-password");
    String token = (String) session.get("refreshToken");
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      List<Future<Boolean>> jobs = new ArrayList<>();
      for (int i = 0; i < 2; i++)
        jobs.add(
            pool.submit(
                () -> {
                  gate.await();
                  try {
                    auth.refresh(token);
                    return true;
                  } catch (ApiException e) {
                    assertEquals(401, e.status);
                    return false;
                  }
                }));
      gate.countDown();
      int winners = 0;
      for (var job : jobs) if (job.get(15, TimeUnit.SECONDS)) winners++;
      assertEquals(1, winners);
    }
  }
}
