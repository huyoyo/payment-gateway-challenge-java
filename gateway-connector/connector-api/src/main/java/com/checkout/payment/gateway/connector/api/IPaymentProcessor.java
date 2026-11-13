package com.checkout.payment.gateway.connector.api;

/**
 * Abstract payment authorization interface implemented by every bank connector.
 *
 * <p>The application layer routes a payment to an implementation by {@link #getBankId()}. The
 * connector owns the bank-specific request/response builders and HTTP client; the application only
 * depends on this interface and the neutral command/outcome models.
 */
public interface IPaymentProcessor {

  /**
   * Stable routing identifier, for example {@code bank-simulator}.
   */
  String getBankId();

  /**
   * Authorizes a payment with the bank represented by this connector.
   *
   * @throws BankUnavailableException when the bank cannot be reached or returns a 5xx response
   * @throws BankIntegrationException when the bank rejects the request or returns an invalid body
   */
  PaymentOutcome performPayments(PaymentCommand command);
}
