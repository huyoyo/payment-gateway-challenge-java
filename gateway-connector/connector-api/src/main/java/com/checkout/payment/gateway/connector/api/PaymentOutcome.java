package com.checkout.payment.gateway.connector.api;

/**
 * Neutral authorization outcome returned by a connector.
 */
public record PaymentOutcome(boolean authorized, String authorizationCode) {
}
