package com.checkout.payment.gateway.connector.api.error;

import java.util.UUID;

/**
 * Raised when a request key is reused with a different payment payload.
 */
public class IdempotencyKeyReusedException extends RuntimeException {

  public IdempotencyKeyReusedException(UUID requestKey) {
    super("requestKey " + requestKey + " was already used with a different payload");
  }
}
