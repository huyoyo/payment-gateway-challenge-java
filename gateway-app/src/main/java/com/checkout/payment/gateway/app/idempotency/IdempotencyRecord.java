package com.checkout.payment.gateway.app.idempotency;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;

/**
 * One requestKey record. FAILED stores the terminal failure so retries do not call the bank again.
 */
public record IdempotencyRecord(
    String fingerprint,
    IdempotencyStatus status,
    PaymentResponse payment,
    RuntimeException failure) {

  public static IdempotencyRecord inProgress(String fingerprint) {
    return new IdempotencyRecord(fingerprint, IdempotencyStatus.IN_PROGRESS, null, null);
  }

  public IdempotencyRecord completed(PaymentResponse payment) {
    return new IdempotencyRecord(fingerprint, IdempotencyStatus.COMPLETED, payment, null);
  }

  public IdempotencyRecord failed(RuntimeException failure) {
    return new IdempotencyRecord(fingerprint, IdempotencyStatus.FAILED, null, failure);
  }
}
