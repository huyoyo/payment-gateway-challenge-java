package com.checkout.payment.gateway.connector.api.error;

/**
 * Public field-level error entry.
 */
public record ApiFieldError(String field, String message) {
}
