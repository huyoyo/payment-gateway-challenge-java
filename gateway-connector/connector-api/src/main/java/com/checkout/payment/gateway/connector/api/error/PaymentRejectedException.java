package com.checkout.payment.gateway.connector.api.error;

import java.util.List;

/**
 * Raised when a request fails validation. No payment is created and the acquiring bank is not
 * called; the API maps this to HTTP 400 with the {@code Rejected} payment status.
 */
public class PaymentRejectedException extends RuntimeException {

  private final List<ValidationError> errors;

  public PaymentRejectedException(List<ValidationError> errors) {
    super("Payment request validation failed");
    this.errors = List.copyOf(errors);
  }

  public List<ValidationError> getErrors() {
    return errors;
  }
}
