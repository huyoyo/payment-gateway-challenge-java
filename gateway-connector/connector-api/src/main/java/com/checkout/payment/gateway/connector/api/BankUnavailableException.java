package com.checkout.payment.gateway.connector.api;

/**
 * The bank could not be reached or returned a 5xx response. The authorization outcome is unknown.
 */
public class BankUnavailableException extends RuntimeException {

  public BankUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
