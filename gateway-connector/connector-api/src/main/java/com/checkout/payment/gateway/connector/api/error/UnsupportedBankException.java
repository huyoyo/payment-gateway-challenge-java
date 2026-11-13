package com.checkout.payment.gateway.connector.api.error;

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Raised when the requested bank id does not match any registered connector. The requested value is
 * deliberately not echoed back to avoid reflecting arbitrary header content.
 */
public class UnsupportedBankException extends RuntimeException {

  public UnsupportedBankException(Iterable<String> supportedBankIds) {
    super("Bank id is not supported. Supported bank ids: " + join(supportedBankIds));
  }

  private static String join(Iterable<String> supportedBankIds) {
    String joined = StreamSupport.stream(supportedBankIds.spliterator(), false)
        .sorted()
        .collect(Collectors.joining(", "));
    return joined.isEmpty() ? "none" : joined;
  }
}
