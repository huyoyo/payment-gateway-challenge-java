package com.checkout.payment.gateway.app.payment.idempotency;

import com.checkout.payment.gateway.app.payment.api.generated.model.CardProcessing;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.Source;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Compact semantic fingerprint used by the requestKey idempotency state machine. Sensitive card
 * values are hashed individually instead of serializing the full request.
 */
@Component("gateway.app.payment.idempotency.payment-request-fingerprint")
public class PaymentRequestFingerprint {

  /**
   * Computes a SHA-256 fingerprint of the payment fields that affect the authorization result.
   *
   * @param request payment request
   * @return hexadecimal fingerprint
   */
  public String fingerprint(CreatePaymentRequest request) {
    String canonical = String.join("|",
        value(request.getBankId()),
        source(request.getSource()),
        value(request.getAmount() == null ? null : request.getAmount().getValue()),
        value(request.getAmount() == null ? null : request.getAmount().getCurrency()),
        value(request.getSoftDescriptor()),
        paymentContext(request));
    return sha256(canonical);
  }

  private static String source(Source source) {
    if (source instanceof CardSource card) {
      return String.join("|", "card", hash(card.getNumber()),
          value(card.getExpiryMonth()), value(card.getExpiryYear()), hash(card.getCvv()),
          value(card.getBrand()), value(card.getName()), value(card.getLastFour()),
          value(card.getBillingAddress()), value(card.getAccountHolder()));
    }
    return "unknown";
  }

  private static String paymentContext(CreatePaymentRequest request) {
    if (request.getPaymentContext() == null) {
      return "";
    }
    String processing = "";
    if (request.getPaymentContext().getProcessing() instanceof CardProcessing card) {
      processing = String.join("|", value(card.getSchema()), value(card.getRoutingNetwork()));
    }
    return String.join("|", value(request.getPaymentContext().getCaptureMode()),
        value(request.getPaymentContext().getTransactionInitiatorType()), processing);
  }

  private static String hash(String value) {
    return value == null ? "" : sha256(value);
  }

  private static String value(Object value) {
    return Objects.toString(value, "");
  }

  private static String sha256(String value) {
    try {
      byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
