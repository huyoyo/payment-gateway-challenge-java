package com.checkout.payment.gateway.connector.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.connector.api.BankIntegrationException;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentResponse;
import org.junit.jupiter.api.Test;

class BankSimulatorResponseBuilderTest {

  private final BankSimulatorResponseBuilder builder = new BankSimulatorResponseBuilder();

  @Test
  void mapsAuthorizedResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(true);
    response.setAuthorizationCode("auth-123");

    PaymentOutcome outcome = builder.build(response);

    assertThat(outcome.authorized()).isTrue();
    assertThat(outcome.authorizationCode()).isEqualTo("auth-123");
  }

  @Test
  void mapsDeclinedResponse() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorized(false);
    response.setAuthorizationCode("");

    PaymentOutcome outcome = builder.build(response);

    assertThat(outcome.authorized()).isFalse();
    assertThat(outcome.authorizationCode()).isEmpty();
  }

  @Test
  void rejectsNullResponse() {
    assertThatThrownBy(() -> builder.build(null))
        .isInstanceOf(BankIntegrationException.class)
        .hasMessageContaining("unreadable");
  }

  @Test
  void rejectsResponseWithoutAuthorizedFlag() {
    BankPaymentResponse response = new BankPaymentResponse();
    response.setAuthorizationCode("auth-123");

    assertThatThrownBy(() -> builder.build(response))
        .isInstanceOf(BankIntegrationException.class)
        .hasMessageContaining("unreadable");
  }
}
