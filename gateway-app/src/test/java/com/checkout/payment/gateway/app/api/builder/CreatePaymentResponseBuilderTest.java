package com.checkout.payment.gateway.app.api.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.api.generated.model.Amount;
import com.checkout.payment.gateway.app.api.generated.model.BankId;
import com.checkout.payment.gateway.app.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatePaymentResponseBuilderTest {

  private final CreatePaymentResponseBuilder builder =
      new CreatePaymentResponseBuilder(new PaymentResponseBuilder());

  @Test
  void omitsCardNumberFromCreateResponse() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setSource(new CardSource("CREDIT_CARD", "2222405343248877", 12, 2099, "123"));
    request.setAmount(new Amount(100, "GBP"));

    PaymentResponse response = builder.build(
        UUID.randomUUID(), BankId.BANK_SIMULATOR, request,
        new PaymentOutcome(true, "auth-123"), OffsetDateTime.now());

    assertThat(response.getSource().getNumber()).isNull();
    assertThat(response.getSource().getCardNumberLastFour()).isEqualTo("8877");
  }
}
