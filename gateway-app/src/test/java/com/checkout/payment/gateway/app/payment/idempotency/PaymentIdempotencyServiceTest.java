package com.checkout.payment.gateway.app.payment.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.observability.PaymentMetrics;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import com.checkout.payment.gateway.connector.api.error.IdempotencyKeyReusedException;
import com.checkout.payment.gateway.connector.api.error.PaymentRequestInProgressException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentIdempotencyServiceTest {

  private final PaymentIdempotencyService service = new PaymentIdempotencyService(
      new PaymentMetrics(new SimpleMeterRegistry()));
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
  void completeAndFailWithoutReservationAreNoOps() {
    PaymentResponse payment = payment();
    service.complete(requestKey, payment);
    service.fail(requestKey, new BankUnavailableException("timeout", null));

    assertThat(service.start(requestKey, "fingerprint")).isEmpty();
  }

  @Test
  void completedResultIsNotOverwrittenByLaterCompleteOrFail() {
    PaymentResponse first = payment();
    PaymentResponse second = payment();
    service.start(requestKey, "fingerprint");
    service.complete(requestKey, first);
    service.complete(requestKey, second);
    service.fail(requestKey, new BankUnavailableException("timeout", null));

    assertThat(service.start(requestKey, "fingerprint")).containsSame(first);
  }

  private static PaymentResponse payment() {
    PaymentResponse payment = new PaymentResponse();
    payment.setId(UUID.randomUUID());
    return payment;
  }

  @Test
  void rejectsSameKeyWithDifferentFingerprint() {
    service.start(requestKey, "fingerprint-a");

    assertThatThrownBy(() -> service.start(requestKey, "fingerprint-b"))
        .isInstanceOf(IdempotencyKeyReusedException.class)
        .hasMessageContaining(requestKey.toString());
  }
}
