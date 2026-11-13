package com.checkout.payment.gateway.app.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import com.checkout.payment.gateway.connector.api.error.IdempotencyKeyReusedException;
import com.checkout.payment.gateway.connector.api.error.PaymentRequestInProgressException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentIdempotencyServiceTest {

  private final PaymentIdempotencyService service = new PaymentIdempotencyService();
  private final UUID requestKey = UUID.randomUUID();

  @Test
  void rejectsDuplicateWhileOriginalIsInProgress() {
    assertThat(service.start(requestKey, "fingerprint")).isEmpty();

    assertThatThrownBy(() -> service.start(requestKey, "fingerprint"))
        .isInstanceOf(PaymentRequestInProgressException.class)
        .hasMessageContaining(requestKey.toString());
  }

  @Test
  void replaysCompletedPayment() {
    PaymentResponse payment = new PaymentResponse();
    payment.setId(UUID.randomUUID());
    service.start(requestKey, "fingerprint");
    service.complete(requestKey, payment);

    assertThat(service.start(requestKey, "fingerprint")).containsSame(payment);
  }

  @Test
  void replaysTerminalFailure() {
    BankUnavailableException failure = new BankUnavailableException("timeout", null);
    service.start(requestKey, "fingerprint");
    service.fail(requestKey, failure);

    assertThatThrownBy(() -> service.start(requestKey, "fingerprint"))
        .isSameAs(failure);
  }

  @Test
  void rejectsSameKeyWithDifferentFingerprint() {
    service.start(requestKey, "fingerprint-a");

    assertThatThrownBy(() -> service.start(requestKey, "fingerprint-b"))
        .isInstanceOf(IdempotencyKeyReusedException.class)
        .hasMessageContaining(requestKey.toString());
  }
}
