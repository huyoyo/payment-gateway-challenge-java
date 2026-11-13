package com.checkout.payment.gateway.app.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import org.junit.jupiter.api.Test;

class PaymentRequestFingerprintTest {

  private final PaymentRequestFingerprint fingerprint = new PaymentRequestFingerprint();

  @Test
  void isStableForSamePayload() {
    CreatePaymentRequest request = request("2222405343248877", "123");

    assertThat(fingerprint.fingerprint(request)).isEqualTo(fingerprint.fingerprint(request));
  }

  @Test
  void changesWhenCardNumberChanges() {
    assertThat(fingerprint.fingerprint(request("2222405343248877", "123")))
        .isNotEqualTo(fingerprint.fingerprint(request("2222405343248888", "123")));
  }

  @Test
  void changesWhenCvvChanges() {
    assertThat(fingerprint.fingerprint(request("2222405343248877", "123")))
        .isNotEqualTo(fingerprint.fingerprint(request("2222405343248877", "456")));
  }

  @Test
  void doesNotExposeSensitiveValues() {
    String value = fingerprint.fingerprint(request("2222405343248877", "123"));

    assertThat(value).doesNotContain("2222405343248877", "123");
  }

  private static CreatePaymentRequest request(String number, String cvv) {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setRequestKey(java.util.UUID.randomUUID());
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(new CardSource("CREDIT_CARD", number, 6, 2030, cvv));
    request.setAmount(new Amount(100, "GBP"));
    return request;
  }
}
