package com.checkout.payment.gateway.connector.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorResponseBuilderTest {

  private final ApiErrorResponseBuilder builder = new ApiErrorResponseBuilder();

  @Test
  void buildsRejectedEnvelopeWithFieldErrors() {
    ApiErrorResponse response = builder.rejected("validation failed",
        List.of(new ApiFieldError("source.number", "invalid")));

    assertThat(response.id()).isNotNull();
    assertThat(response.status()).isEqualTo("Rejected");
    assertThat(response.code()).isEqualTo("PAYMENT_REJECTED");
    assertThat(response.timestamp()).isNotNull();
    assertThat(response.errors()).singleElement()
        .satisfies(error -> assertThat(error.field()).isEqualTo("source.number"));
  }

  @Test
  void buildsUnknownEnvelopeForAmbiguousBankOutcome() {
    ApiErrorResponse response = builder.unknown("ACQUIRING_BANK_UNAVAILABLE", "unknown");

    assertThat(response.status()).isEqualTo("Unknown");
    assertThat(response.code()).isEqualTo("ACQUIRING_BANK_UNAVAILABLE");
    assertThat(response.errors()).isEmpty();
  }

  @Test
  void buildsErrorEnvelopeWithoutFieldErrors() {
    ApiErrorResponse response = builder.error("PAYMENT_NOT_FOUND", "missing");

    assertThat(response.code()).isEqualTo("PAYMENT_NOT_FOUND");
    assertThat(response.errors()).isEmpty();
  }
}
