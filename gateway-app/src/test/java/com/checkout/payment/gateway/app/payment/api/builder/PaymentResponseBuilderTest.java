package com.checkout.payment.gateway.app.payment.api.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
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
  void buildsAuthorizedResponseWithoutPan() {
    UUID id = UUID.randomUUID();
    CreatePaymentRequest request = request(new CardSource("CREDIT_CARD",
        "2222405343248877", 12, 2099, "123"));
    ((CardSource) request.getSource()).setBrand("VISA");

    PaymentResponse response = builder.build(
        id, BankId.BANK_SIMULATOR, request,
        new PaymentOutcome(true, "auth-123"), PROCESSED_ON);

    CardSource source = (CardSource) response.getSource();
    assertThat(response.getId()).isEqualTo(id);
    assertThat(response.getBankId()).isEqualTo(BankId.BANK_SIMULATOR);
    assertThat(response.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(response.getAmount().getValue()).isEqualTo(100);
    assertThat(source.getType()).isEqualTo("CREDIT_CARD");
    assertThat(source.getBrand()).isEqualTo("VISA");
    assertThat(source.getLastFour()).isEqualTo("8877");
    assertThat(source.getNumber()).isNull();
    assertThat(source.getExpiryMonth()).isEqualTo(12);
    assertThat(source.getExpiryYear()).isEqualTo(2099);
    assertThat(response.getProcessedOn()).isEqualTo(PROCESSED_ON);
    assertThat(response.getProcessing().getAuthCode()).isEqualTo("auth-123");
  }

  @Test
  void buildsDeclinedResponseWithoutProcessing() {
    PaymentResponse response = builder.build(UUID.randomUUID(), BankId.BANK_SIMULATOR,
        request(new CardSource("CREDIT_CARD", "2222405343248877", 12, 2099, "123")),
        new PaymentOutcome(false, ""), PROCESSED_ON);

    assertThat(response.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(response.getProcessing()).isNull();
  }

  @Test
  void acceptsNullAmountAndNullSource() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);

    PaymentResponse response = builder.build(UUID.randomUUID(), BankId.BANK_SIMULATOR,
        request, new PaymentOutcome(true, null), PROCESSED_ON);

    assertThat(response.getAmount()).isNull();
    assertThat(response.getSource()).isInstanceOf(CardSource.class);
    assertThat(((CardSource) response.getSource()).getType()).isNull();
    assertThat(response.getProcessing()).isNull();
  }

  @Test
  void addsAndRemovesMaskedNumberForCardSource() {
    PaymentResponse response = new PaymentResponse();
    response.setSource(new CardSource("CREDIT_CARD", "2222405343248877", 12, 2099, "123"));

    builder.withMaskedNumber(response, "**** **** **** 8877", "8877");

    CardSource source = (CardSource) response.getSource();
    assertThat(source.getNumber()).isEqualTo("**** **** **** 8877");
    assertThat(source.getLastFour()).isEqualTo("8877");

    builder.withoutCardNumber(response);
    assertThat(source.getNumber()).isNull();
  }

  @Test
  void toleratesNullSourceWhenAddingOrRemovingMask() {
    PaymentResponse response = new PaymentResponse();

    assertThat(builder.withMaskedNumber(response, "mask", "1234")).isSameAs(response);
    assertThat(builder.withoutCardNumber(response)).isSameAs(response);
    assertThat(response.getSource()).isNull();
  }

  private static CreatePaymentRequest request(CardSource source) {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(source);
    request.setAmount(new Amount(100, "GBP"));
    return request;
  }
}
