package com.example.backend.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  public record Registration(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record Login(
      @NotBlank @jakarta.validation.constraints.Email String email,
      @NotBlank @Size(max = 72) String password) {}

  public record Token(@NotBlank @Size(max = 256) String token) {}

  public record Email(
      @NotBlank @jakarta.validation.constraints.Email @Size(max = 254) String email) {}

  public record Password(
      @NotBlank @Size(max = 72) String currentPassword,
      @NotBlank @Size(min = 12, max = 72) String newPassword) {}

  public record Reset(
      @NotBlank @Size(max = 256) String token,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/register")
  public Object register(@Valid @RequestBody Registration r) {
    return auth.register(r.name(), r.email(), r.password());
  }

  @PostMapping("/login")
  public Object login(@Valid @RequestBody Login r) {
    return auth.login(r.email(), r.password());
  }

  @PostMapping("/refresh")
  public Object refresh(@Valid @RequestBody Token r) {
    return auth.refresh(r.token());
  }

  @GetMapping("/me")
  public Object me() {
    return auth.profile(Actor.current().id());
  }

  @PostMapping("/logout")
  public Object logout() {
    auth.logout(Actor.current().id());
    return Map.of("message", "Signed out from all sessions");
  }

  @PostMapping("/password")
  public Object password(@Valid @RequestBody Password r) {
    auth.password(Actor.current().id(), r.currentPassword(), r.newPassword());
    return Map.of("message", "Password changed; sign in again");
  }

  @PostMapping("/forgot-password")
  public Object forgot(@Valid @RequestBody Email r) {
    auth.forgot(r.email());
    return Map.of("message", "If the account exists, your recovery request has been recorded");
  }

  @PostMapping("/reset-password")
  public Object reset(@Valid @RequestBody Reset r) {
    auth.reset(r.token(), r.password());
    return Map.of("message", "Password reset; sign in again");
  }
}
