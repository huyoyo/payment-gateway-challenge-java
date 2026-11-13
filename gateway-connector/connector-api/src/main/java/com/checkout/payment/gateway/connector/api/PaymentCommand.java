package com.checkout.payment.gateway.connector.api;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Neutral authorization command passed from the application layer to a connector. The connector's
 * request builder turns it into the bank-specific wire format.
 */
public record PaymentCommand(
    String cardNumber,
    YearMonth expiryDate,
    String currency,
    int amount,
    String cvv) {

  public PaymentCommand {
    Objects.requireNonNull(cardNumber, "cardNumber must not be null");
    Objects.requireNonNull(expiryDate, "expiryDate must not be null");
    Objects.requireNonNull(currency, "currency must not be null");
    Objects.requireNonNull(cvv, "cvv must not be null");
  }
}
