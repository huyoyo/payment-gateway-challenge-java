package com.checkout.payment.gateway.app.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.WalletSource;
import com.checkout.payment.gateway.connector.api.error.ValidationError;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentValidatorTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2025-06-15T10:00:00Z"), ZoneOffset.UTC);

  private final PaymentValidator validator = new PaymentValidator(FIXED_CLOCK);

  @Test
  void acceptsFutureCardExpiry() {
    assertThat(validator.validate(request(8, 2025))).isEmpty();
  }

  @Test
  void acceptsCurrentMonthCardExpiry() {
    assertThat(validator.validate(request(6, 2025))).isEmpty();
  }

  @Test
  void rejectsExpiredCardWithNestedFieldPath() {
    List<ValidationError> errors = validator.validate(request(5, 2025));

    assertThat(errors).singleElement()
        .satisfies(error -> {
          assertThat(error.field()).isEqualTo("source.expiryYear");
          assertThat(error.message()).contains("future");
        });
  }

  @Test
  void doesNotApplyExpiryValidationToWalletSource() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(new WalletSource("WALLET", "wallet-token"));
    request.setAmount(new Amount(100, "GBP"));

    assertThat(validator.validate(request)).isEmpty();
  }

  private static CreatePaymentRequest request(int expiryMonth, int expiryYear) {
    CardSource card = new CardSource("CREDIT_CARD",
        "2222405343248877", expiryMonth, expiryYear, "123");
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(card);
    request.setAmount(new Amount(100, "GBP"));
    return request;
  }
}
