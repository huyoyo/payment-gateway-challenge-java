package com.checkout.payment.gateway.app.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentStatus;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class PaymentMetricsTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final PaymentMetrics metrics = new PaymentMetrics(registry);

  @Test
  void recordsPaymentOutcomeAndLatencyWithBoundedTags() {
    PaymentResponse payment = new PaymentResponse();
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setBankId(BankId.BANK_SIMULATOR);

    Timer.Sample sample = metrics.startAuthorization();
    metrics.paymentCreated(payment);
    metrics.authorizationCompleted(sample, BankId.BANK_SIMULATOR, payment.getStatus());

    assertThat(registry.get("payments.created")
        .tag("status", "Authorized")
        .tag("bank", "bank-simulator")
        .counter()
        .count()).isEqualTo(1.0);
    assertThat(registry.get("payment.authorization.duration")
        .tag("status", "Authorized")
        .timer()
        .count()).isEqualTo(1);
  }

  @Test
  void usesUnknownTagsForMissingStatusOrBank() {
    PaymentResponse payment = new PaymentResponse();
    Timer.Sample sample = metrics.startAuthorization();

    metrics.paymentCreated(payment);
    metrics.authorizationCompleted(sample, null, null);

    assertThat(registry.get("payments.created")
        .tag("status", "unknown")
        .tag("bank", "unknown")
        .counter()
        .count()).isEqualTo(1.0);
    assertThat(registry.get("payment.authorization.duration")
        .tag("bank", "unknown")
        .tag("status", "error")
        .timer()
        .count()).isEqualTo(1);
  }

  @Test
  void recordsIdempotencyReplayAndConflict() {
    metrics.idempotencyReplay("completed");
    metrics.idempotencyConflict("in_progress");

    assertThat(registry.get("payment.idempotency.replays")
        .tag("outcome", "completed")
        .counter()
        .count()).isEqualTo(1.0);
    assertThat(registry.get("payment.idempotency.conflicts")
        .tag("reason", "in_progress")
        .counter()
        .count()).isEqualTo(1.0);
  }
}
