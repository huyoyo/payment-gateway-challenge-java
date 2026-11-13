package com.checkout.payment.gateway.app.payment.api.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentStatus;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentResponseBuilderTest {

  private static final OffsetDateTime PROCESSED_ON =
      OffsetDateTime.ofInstant(Instant.parse("2025-06-15T10:00:00Z"), ZoneOffset.UTC);
  private final PaymentResponseBuilder builder = new PaymentResponseBuilder();

  @Test
  void buildsGeneratedResponseFromCardSourceAndOutcome() {
    UUID id = UUID.randomUUID();
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(new CardSource("CREDIT_CARD",
        "2222405343248877", 12, 2099, "123"));
    CardSource card = (CardSource) request.getSource();
    card.setBrand("VISA");
    request.setAmount(new Amount(100, "GBP"));

    PaymentResponse response = builder.build(
        id, BankId.BANK_SIMULATOR, request,
        new PaymentOutcome(true, "auth-123"), PROCESSED_ON);

    assertThat(response.getId()).isEqualTo(id);
    assertThat(response.getBankId()).isEqualTo(BankId.BANK_SIMULATOR);
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getAmount().getValue()).isEqualTo(100);
    assertThat(response.getAmount().getCurrency()).isEqualTo("GBP");
    assertThat(response.getSource().getType()).isEqualTo(PaymentSource.TypeEnum.CREDIT_CARD);
    assertThat(response.getSource().getBrand()).isEqualTo("VISA");
    assertThat(response.getSource().getCardNumberLastFour()).isEqualTo("8877");
    assertThat(response.getSource().getNumber()).isNull();
    assertThat(response.getSource().getExpiryMonth()).isEqualTo(12);
    assertThat(response.getSource().getExpiryYear()).isEqualTo(2099);
    assertThat(response.getProcessedOn()).isEqualTo(PROCESSED_ON);
    assertThat(response.getProcessing().getAuthCode()).isEqualTo("auth-123");
  }
}
