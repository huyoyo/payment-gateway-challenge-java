package com.checkout.payment.gateway.app.payment.observability;

import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Payment-domain metrics with bounded tags only. Card data and request keys never become tags.
 */
@Component("gateway.app.payment.observability.payment-metrics")
@RequiredArgsConstructor
public class PaymentMetrics {

  private static final String UNKNOWN = "unknown";
  private static final String ERROR = "error";

  private final MeterRegistry meterRegistry;

  /**
   * Starts a timer around connector authorization and persistence.
   *
   * @return timer sample
   */
  public Timer.Sample startAuthorization() {
    return Timer.start(meterRegistry);
  }

  /**
   * Records the final payment outcome.
   *
   * @param payment completed payment
   */
  public void paymentCreated(PaymentResponse payment) {
    Counter.builder("payments.created")
        .description("Payments processed by the gateway")
        .tag("status", tag(payment.getStatus()))
        .tag("bank", payment.getBankId() == null ? UNKNOWN : payment.getBankId().getValue())
        .register(meterRegistry)
        .increment();
  }

  /**
   * Records authorization latency for successful and failed attempts.
   *
   * @param sample timer sample
   * @param bankId selected bank
   * @param status final status, or null when the attempt failed
   */
  public void authorizationCompleted(Timer.Sample sample, BankId bankId, PaymentStatus status) {
    Timer timer = Timer.builder("payment.authorization.duration")
        .description("Time spent authorizing and persisting a payment")
        .tag("bank", bankId == null ? UNKNOWN : bankId.getValue())
        .tag("status", status == null ? ERROR : status.getValue())
        .register(meterRegistry);
    sample.stop(timer);
  }

  /**
   * Records a replay of an existing idempotency result.
   *
   * @param outcome completed or failed
   */
  public void idempotencyReplay(String outcome) {
    Counter.builder("payment.idempotency.replays")
        .description("Idempotent request replays")
        .tag("outcome", outcome)
        .register(meterRegistry)
        .increment();
  }

  /**
   * Records an idempotency conflict.
   *
   * @param reason fingerprint or in_progress
   */
  public void idempotencyConflict(String reason) {
    Counter.builder("payment.idempotency.conflicts")
        .description("Idempotency requests rejected because of a conflict")
        .tag("reason", reason)
        .register(meterRegistry)
        .increment();
  }

  private static String tag(PaymentStatus status) {
    return status == null ? UNKNOWN : status.getValue();
  }
}
