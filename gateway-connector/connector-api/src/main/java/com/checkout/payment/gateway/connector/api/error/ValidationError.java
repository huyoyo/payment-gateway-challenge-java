package com.checkout.payment.gateway.connector.api.error;

/**
 * A single field-level validation failure. Field names use the public camelCase API names so
 * they can be returned to the caller without translation.
 */
public record ValidationError(String field, String message) {
}
