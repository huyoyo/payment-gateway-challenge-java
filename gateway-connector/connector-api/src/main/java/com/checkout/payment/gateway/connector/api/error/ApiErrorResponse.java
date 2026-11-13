package com.checkout.payment.gateway.connector.api.error;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Shared public error response returned by every non-2xx API path.
 */
public record ApiErrorResponse(
    UUID id,
    String status,
    OffsetDateTime timestamp,
    String code,
    String message,
    List<ApiFieldError> errors) {
}
