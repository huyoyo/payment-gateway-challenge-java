package com.checkout.payment.gateway.connector.api.error;

import com.checkout.payment.gateway.connector.api.BankIntegrationException;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import lombok.RequiredArgsConstructor;

/**
 * Shared error advice for all public API adapters. It is intentionally part of connector-api so the
 * application layer does not own the public error envelope.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ApiErrorResponseBuilder errorResponseBuilder;

  @ExceptionHandler(PaymentRejectedException.class)
  public ResponseEntity<ApiErrorResponse> handlePaymentRejected(PaymentRejectedException ex) {
    List<ApiFieldError> errors = ex.getErrors().stream()
        .map(error -> new ApiFieldError(error.field(), error.message()))
        .toList();
    LOG.info("Payment rejected with {} validation error(s)", errors.size());
    return ResponseEntity.badRequest()
        .body(errorResponseBuilder.rejected("Payment request validation failed", errors));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiErrorResponse> handleBeanValidation(MethodArgumentNotValidException ex) {
    List<ApiFieldError> errors = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
        .toList();
    LOG.info("Payment rejected by OpenAPI validation with {} error(s)", errors.size());
    return ResponseEntity.badRequest()
        .body(errorResponseBuilder.rejected("Payment request validation failed", errors));
  }

  @ExceptionHandler(IdempotencyKeyReusedException.class)
  public ResponseEntity<ApiErrorResponse> handleIdempotencyKeyReused(
      IdempotencyKeyReusedException ex) {
    LOG.warn("Idempotency key reused with a different payload");
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorResponseBuilder.error("IDEMPOTENCY_KEY_REUSED", ex.getMessage()));
  }

  @ExceptionHandler(PaymentRequestInProgressException.class)
  public ResponseEntity<ApiErrorResponse> handlePaymentRequestInProgress(
      PaymentRequestInProgressException ex) {
    LOG.info("Duplicate requestKey is still in progress");
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(errorResponseBuilder.error("REQUEST_IN_PROGRESS", ex.getMessage()));
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handlePaymentNotFound(PaymentNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(errorResponseBuilder.error("PAYMENT_NOT_FOUND", ex.getMessage()));
  }

  @ExceptionHandler(UnsupportedBankException.class)
  public ResponseEntity<ApiErrorResponse> handleUnsupportedBank(UnsupportedBankException ex) {
    LOG.warn("Requested bank is not configured");
    return ResponseEntity.badRequest()
        .body(errorResponseBuilder.error("UNSUPPORTED_BANK", ex.getMessage()));
  }

  @ExceptionHandler(BankUnavailableException.class)
  public ResponseEntity<ApiErrorResponse> handleBankUnavailable(BankUnavailableException ex) {
    LOG.warn("Acquiring bank unavailable: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(errorResponseBuilder.unknown("ACQUIRING_BANK_UNAVAILABLE",
            "Acquiring bank did not return a definitive outcome; retry or reconcile"));
  }

  @ExceptionHandler(BankIntegrationException.class)
  public ResponseEntity<ApiErrorResponse> handleBankIntegration(BankIntegrationException ex) {
    // Do not log stack traces: an upstream response could echo card data.
    LOG.error("Acquiring bank integration error: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(errorResponseBuilder.unknown("ACQUIRING_BANK_ERROR",
            "Acquiring bank could not process the request"));
  }

  @ExceptionHandler({
      HttpMessageNotReadableException.class,
      MethodArgumentTypeMismatchException.class
  })
  public ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception ex) {
    LOG.debug("Invalid request rejected: {}", ex.getClass().getSimpleName());
    return ResponseEntity.badRequest()
        .body(errorResponseBuilder.error("INVALID_REQUEST",
            "Request body or path parameter is invalid"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(errorResponseBuilder.error("METHOD_NOT_ALLOWED",
            "HTTP method is not supported for this endpoint"));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiErrorResponse> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(errorResponseBuilder.error("UNSUPPORTED_MEDIA_TYPE",
            "Content type is not supported; use application/json"));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
    LOG.error("Unexpected error while processing request", ex);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(errorResponseBuilder.unknown("INTERNAL_ERROR", "An unexpected error occurred"));
  }
}
