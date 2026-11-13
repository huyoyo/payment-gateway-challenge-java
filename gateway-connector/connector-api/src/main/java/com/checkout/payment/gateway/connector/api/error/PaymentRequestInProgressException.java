package com.checkout.payment.gateway.connector.api.error;

import java.util.UUID;

/**
 * Raised when a duplicate request arrives while the original request is still being processed.
 */
public class PaymentRequestInProgressException extends RuntimeException {

  public PaymentRequestInProgressException(UUID requestKey) {
    super("requestKey " + requestKey + " is already in progress");
  }
}
