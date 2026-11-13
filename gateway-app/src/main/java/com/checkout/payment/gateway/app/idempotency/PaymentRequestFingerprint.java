package com.checkout.payment.gateway.app.idempotency;

import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * Stable SHA-256 fingerprint of the semantic request payload. A requestKey may only be replayed
 * with the same fingerprint.
 */
@Component("gateway.app.idempotency.payment-request-fingerprint")
public class PaymentRequestFingerprint {

  private final ObjectMapper objectMapper;

  public PaymentRequestFingerprint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Computes a stable SHA-256 fingerprint of the payment payload.
   *
   * @param request payment request
   * @return hexadecimal fingerprint
   */
  public String fingerprint(CreatePaymentRequest request) {
    try {
      byte[] serialized = objectMapper.writeValueAsBytes(request);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized));
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("Could not fingerprint payment request", ex);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
