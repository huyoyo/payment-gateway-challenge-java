package com.checkout.payment.gateway.connector.api.error;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Builds the shared public error envelope. Keeping this outside the application layer keeps the
 * error contract consistent for all inbound adapters.
 */
@Component("gateway.connector.api.error.api-error-response-builder")
public class ApiErrorResponseBuilder {

  private static final Logger LOG = LoggerFactory.getLogger(ApiErrorResponseBuilder.class);

  public ApiErrorResponse rejected(String message, List<ApiFieldError> errors) {
    return build("Rejected", "PAYMENT_REJECTED", message, errors);
  }

  public ApiErrorResponse error(String code, String message) {
    return build("Rejected", code, message, List.of());
  }

  public ApiErrorResponse unknown(String code, String message) {
    return build("Unknown", code, message, List.of());
  }

  private ApiErrorResponse build(String status, String code, String message,
      List<ApiFieldError> errors) {
    UUID errorId = UUID.randomUUID();
    LOG.info("Created error response id={} status={} code={}", errorId, status, code);
    return new ApiErrorResponse(
        errorId,
        status,
        OffsetDateTime.now(ZoneOffset.UTC),
        code,
        message,
        errors);
  }
}
