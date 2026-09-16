package com.example.backend.security;

import com.example.backend.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetDelivery {
  private final JavaMailSender mail;
  private final boolean enabled;
  private final String origin, from;

  public PasswordResetDelivery(
      JavaMailSender mail,
      @Value("${fleetflow.mail-enabled}") boolean enabled,
      @Value("${fleetflow.cors-origin}") String origin,
      @Value("${fleetflow.mail-from}") String from) {
    this.mail = mail;
    this.enabled = enabled;
    this.origin = origin;
    this.from = from;
  }

  public void requireConfigured() {
    if (!enabled)
      throw new ApiException(
          503,
          "EMAIL_NOT_CONFIGURED",
          "Password recovery requires configured email delivery; contact your administrator");
  }

  public void send(String email, String token) {
    var message = new SimpleMailMessage();
    message.setTo(email);
    message.setFrom(from);
    message.setSubject("Reset your FleetFlow password");
    message.setText(
        "A password reset was requested for your FleetFlow account. Open "
            + origin
            + "/reset-password#token="
            + token
            + " within 20 minutes. Ignore this email if you did not request it.");
    try {
      mail.send(message);
    } catch (org.springframework.mail.MailException e) {
      throw new ApiException(
          503, "EMAIL_UNAVAILABLE", "Password recovery email could not be delivered; try later");
    }
  }
}
