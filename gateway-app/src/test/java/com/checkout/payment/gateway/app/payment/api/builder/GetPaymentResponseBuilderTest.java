package com.checkout.payment.gateway.app.payment.api.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentProcessing;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentStatus;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRecord;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetPaymentResponseBuilderTest {

  private final GetPaymentResponseBuilder builder =
      new GetPaymentResponseBuilder(new PaymentResponseBuilder());

  @Test
  void addsMaskedNumberInPlaceUsingCommonBuilder() {
    PaymentResponse stored = new PaymentResponse();
    stored.setId(UUID.randomUUID());
    stored.setBankId(BankId.BANK_SIMULATOR);
    stored.setStatus(PaymentStatus.AUTHORIZED);
    Amount amount = new Amount(100, "GBP");
    stored.setAmount(amount);
    OffsetDateTime processedOn = OffsetDateTime.parse("2025-06-15T10:00:00Z");
    stored.setProcessedOn(processedOn);
    PaymentProcessing processing = new PaymentProcessing();
    processing.setAuthCode("auth-123");
    stored.setProcessing(processing);
    PaymentSource source = new PaymentSource();
    source.setType(PaymentSource.TypeEnum.CREDIT_CARD);
    source.setBrand("VISA");
    source.setCardNumberLastFour("8877");
    source.setExpiryMonth(12);
    source.setExpiryYear(2099);
    stored.setSource(source);

    PaymentResponse response = builder.build(new PaymentRecord(
        stored, "**** **** **** 8877", "8877"));

    assertThat(response).isSameAs(stored);
    assertThat(response.getAmount()).isSameAs(amount);
    assertThat(response.getProcessing()).isSameAs(processing);
    assertThat(response.getSource().getNumber()).isEqualTo("**** **** **** 8877");
    assertThat(response.getSource().getCardNumberLastFour()).isEqualTo("8877");
  }
}
