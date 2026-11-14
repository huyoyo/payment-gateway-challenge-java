package com.checkout.payment.gateway.connector.api.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.connector.api.BankIntegrationException;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler(new ApiErrorResponseBuilder());
  }

  @Test
  void mapsPaymentRejectionAndBeanValidationToBadRequest() {
    ResponseEntity<ApiErrorResponse> rejected = handler.handlePaymentRejected(
        new PaymentRejectedException(List.of(new ValidationError("source.number", "invalid"))));

    assertError(rejected, HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED");
    assertThat(rejected.getBody().errors())
        .containsExactly(new ApiFieldError("source.number", "invalid"));

    BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
    bindingResult.addError(new FieldError("request", "amount.value", "must be positive"));
    MethodArgumentNotValidException validationException =
        mock(MethodArgumentNotValidException.class);
    when(validationException.getBindingResult()).thenReturn(bindingResult);

    ResponseEntity<ApiErrorResponse> beanValidation =
        handler.handleBeanValidation(validationException);
    assertError(beanValidation, HttpStatus.BAD_REQUEST, "PAYMENT_REJECTED");
    assertThat(beanValidation.getBody().errors())
        .containsExactly(new ApiFieldError("amount.value", "must be positive"));
  }

  @Test
  void mapsIdempotencyConflictsToConflict() {
    ResponseEntity<ApiErrorResponse> reused = handler.handleIdempotencyKeyReused(
        new IdempotencyKeyReusedException(UUID.randomUUID()));
    assertError(reused, HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED");

    ResponseEntity<ApiErrorResponse> inProgress = handler.handlePaymentRequestInProgress(
        new PaymentRequestInProgressException(UUID.randomUUID()));
    assertError(inProgress, HttpStatus.CONFLICT, "REQUEST_IN_PROGRESS");
  }

  @Test
  void mapsNotFoundAndUnsupportedBank() {
    ResponseEntity<ApiErrorResponse> notFound =
        handler.handlePaymentNotFound(new PaymentNotFoundException(UUID.randomUUID()));
    assertError(notFound, HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND");

    ResponseEntity<ApiErrorResponse> unsupported =
        handler.handleUnsupportedBank(new UnsupportedBankException(List.of("bank-z", "bank-a")));
    assertError(unsupported, HttpStatus.BAD_REQUEST, "UNSUPPORTED_BANK");
    assertThat(unsupported.getBody().message()).contains("bank-a, bank-z");
  }

  @Test
  void mapsBankFailuresToGatewayErrors() {
    ResponseEntity<ApiErrorResponse> unavailable = handler.handleBankUnavailable(
        new BankUnavailableException("offline", null));
    assertError(unavailable, HttpStatus.SERVICE_UNAVAILABLE, "Unknown",
        "ACQUIRING_BANK_UNAVAILABLE");
    assertThat(unavailable.getBody().message()).doesNotContain("offline");

    ResponseEntity<ApiErrorResponse> integration = handler.handleBankIntegration(
        new BankIntegrationException("rejected"));
    assertError(integration, HttpStatus.BAD_GATEWAY, "Unknown",
        "ACQUIRING_BANK_ERROR");
  }

  @Test
  void mapsMalformedRequestsAndUnsupportedHttpMethodsAndMediaTypes() {
    ResponseEntity<ApiErrorResponse> unreadable =
        handler.handleInvalidRequest(new HttpMessageNotReadableException("invalid"));
    assertError(unreadable, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");

    ResponseEntity<ApiErrorResponse> mismatch =
        handler.handleInvalidRequest(mock(MethodArgumentTypeMismatchException.class));
    assertError(mismatch, HttpStatus.BAD_REQUEST, "INVALID_REQUEST");

    ResponseEntity<ApiErrorResponse> method = handler.handleMethodNotSupported(
        new HttpRequestMethodNotSupportedException("PATCH"));
    assertError(method, HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED");

    ResponseEntity<ApiErrorResponse> media = handler.handleMediaTypeNotSupported(
        new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN_VALUE));
    assertError(media, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
  }

  @Test
  void mapsUnexpectedErrorsToInternalServerError() {
    ResponseEntity<ApiErrorResponse> response =
        handler.handleUnexpected(new IllegalStateException("unexpected"));

    assertError(response, HttpStatus.INTERNAL_SERVER_ERROR,"Unknown", "INTERNAL_ERROR");
    assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
  }

  @Test
  void buildsSharedErrorEnvelopeWithTimestampAndEmptyErrorList() {
    ApiErrorResponseBuilder builder = new ApiErrorResponseBuilder();

    ApiErrorResponse rejected = builder.rejected(
        "invalid request", List.of(new ApiFieldError("field", "message")));
    assertThat(rejected.id()).isNotNull();
    assertThat(rejected.timestamp()).isNotNull();
    assertThat(rejected.status()).isEqualTo("Rejected");
    assertThat(rejected.code()).isEqualTo("PAYMENT_REJECTED");
    assertThat(rejected.message()).isEqualTo("invalid request");
    assertThat(rejected.errors()).containsExactly(new ApiFieldError("field", "message"));

    ApiErrorResponse error = builder.error("ERROR", "failed");
    assertThat(error.code()).isEqualTo("ERROR");
    assertThat(error.errors()).isEmpty();
  }

  private static void assertError(ResponseEntity<ApiErrorResponse> response, HttpStatus status,
      String code) {
    assertError(response, status, "Rejected", code);
  }

  private static void assertError(ResponseEntity<ApiErrorResponse> response, HttpStatus status,
      String envelopeStatus, String code) {
    assertThat(response.getStatusCode()).isEqualTo(status);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().id()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(envelopeStatus);
    assertThat(response.getBody().code()).isEqualTo(code);
  }
}
