package com.checkout.payment.gateway.connector.api;

/**
 * The bank rejected the request or returned a body the connector cannot interpret.
 */
public class BankIntegrationException extends RuntimeException {

  public BankIntegrationException(String message) {
    super(message);
  }

  public BankIntegrationException(String message, Throwable cause) {
    super(message, cause);
  }
}
