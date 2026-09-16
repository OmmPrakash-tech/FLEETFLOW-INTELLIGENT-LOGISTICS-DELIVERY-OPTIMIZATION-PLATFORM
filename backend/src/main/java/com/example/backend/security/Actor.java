package com.example.backend.security;

import com.example.backend.common.ApiException;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

public record Actor(UUID id, String role) {
  public static Actor current() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof Actor actor))
      throw new ApiException(401, "UNAUTHORIZED", "Sign in required");
    return actor;
  }

  public boolean staff() {
    return role.equals("ADMIN") || role.equals("OPERATOR");
  }

  public void requireStaff() {
    if (!staff()) throw ApiException.forbidden();
  }

  public void requireAdmin() {
    if (!role.equals("ADMIN")) throw ApiException.forbidden();
  }
}
