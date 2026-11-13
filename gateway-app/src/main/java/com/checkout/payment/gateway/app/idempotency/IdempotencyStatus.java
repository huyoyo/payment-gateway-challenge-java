package com.checkout.payment.gateway.app.idempotency;

public enum IdempotencyStatus {
  IN_PROGRESS,
  COMPLETED,
  FAILED
}
