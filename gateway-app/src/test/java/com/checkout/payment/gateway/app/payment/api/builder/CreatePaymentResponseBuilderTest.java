package com.checkout.payment.gateway.app.payment.api.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreatePaymentResponseBuilderTest {

  private static final Instant NOW = Instant.parse("2025-06-15T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private final CreatePaymentResponseBuilder builder =
      new CreatePaymentResponseBuilder(new PaymentResponseBuilder(), CLOCK);

  @Test
  void omitsCardNumberAndSetsProcessingTime() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setSource(new CardSource("CREDIT_CARD", "2222405343248877", 12, 2099, "123"));
    request.setAmount(new Amount(100, "GBP"));

    PaymentResponse response = builder.build(
        UUID.randomUUID(), BankId.BANK_SIMULATOR, request, new PaymentOutcome(true, "auth-123"));

    CardSource source = (CardSource) response.getSource();
    assertThat(source.getNumber()).isNull();
    assertThat(source.getLastFour()).isEqualTo("8877");
    assertThat(response.getProcessedOn())
        .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
  }

  @Test
  void replayRemovesMaskedNumberInPlace() {
    PaymentResponse stored = new PaymentResponse();
    CardSource source = new CardSource("CREDIT_CARD", "2222405343248877", 12, 2099, "123");
    source.setNumber("**** **** **** 8877");
    stored.setSource(source);

    PaymentResponse replay = builder.buildReplay(stored);

    assertThat(replay).isSameAs(stored);
    assertThat(source.getNumber()).isNull();
  }

  @Test
  void replayToleratesMissingSource() {
    PaymentResponse stored = new PaymentResponse();

    assertThat(builder.buildReplay(stored)).isSameAs(stored);
  }
}
