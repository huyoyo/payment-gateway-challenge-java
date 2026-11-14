package com.checkout.payment.gateway.app.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardProcessing;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentContext;
import com.checkout.payment.gateway.app.payment.api.generated.model.Source;
import org.junit.jupiter.api.Test;

class PaymentRequestFingerprintTest {

  private final PaymentRequestFingerprint fingerprint = new PaymentRequestFingerprint();

  @Test
  void isStableForSamePayload() {
    CreatePaymentRequest request = request("2222405343248877", "123");

    assertThat(fingerprint.fingerprint(request)).isEqualTo(fingerprint.fingerprint(request));
  }

  @Test
  void changesWhenSensitiveOrSemanticFieldsChange() {
    String base = fingerprint.fingerprint(request("2222405343248877", "123"));

    assertThat(base)
        .isNotEqualTo(fingerprint.fingerprint(request("2222405343248888", "123")))
        .isNotEqualTo(fingerprint.fingerprint(request("2222405343248877", "456")));
  }

  @Test
  void changesWhenOptionalCardAndContextFieldsChange() {
    CreatePaymentRequest first = request("2222405343248877", "123");
    ((CardSource) first.getSource()).setBrand("VISA");
    ((CardSource) first.getSource()).setLastFour("8877");
    PaymentContext context = new PaymentContext();
    context.setCaptureMode(PaymentContext.CaptureModeEnum.SINGLE);
    CardProcessing processing = new CardProcessing();
    processing.setSchema("CardProcessing");
    context.setProcessing(processing);
    first.setPaymentContext(context);
    first.setSoftDescriptor("order-1");

    CreatePaymentRequest second = request("2222405343248877", "123");
    ((CardSource) second.getSource()).setBrand("MASTERCARD");
    second.setPaymentContext(new PaymentContext());

    assertThat(fingerprint.fingerprint(first)).isNotEqualTo(fingerprint.fingerprint(second));
  }

  @Test
  void handlesMissingSourceAmountAndContext() {
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);

    assertThat(fingerprint.fingerprint(request)).isNotBlank();
    request.setSource(mock(Source.class));
    assertThat(fingerprint.fingerprint(request)).isNotBlank();
    request.setSource(null);
    request.setPaymentContext(new PaymentContext());
    assertThat(fingerprint.fingerprint(request)).isNotBlank();
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
