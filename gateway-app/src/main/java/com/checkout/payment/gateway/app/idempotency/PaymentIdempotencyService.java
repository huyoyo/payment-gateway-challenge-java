package com.checkout.payment.gateway.app.idempotency;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.error.IdempotencyKeyReusedException;
import com.checkout.payment.gateway.connector.api.error.PaymentRequestInProgressException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * In-memory idempotency coordinator for the exercise. Production should use a durable store with a
 * lease/in-progress timeout and unique constraint on requestKey.
 */
@Service("gateway.app.idempotency.payment-idempotency-service")
public class PaymentIdempotencyService {

  private final Map<UUID, IdempotencyRecord> records = new ConcurrentHashMap<>();

  /**
   * Atomically reserves the key. Returns the stored payment when the key is already completed.
   */
  public Optional<PaymentResponse> start(UUID requestKey, String fingerprint) {
    Objects.requireNonNull(requestKey, "requestKey must not be null");
    IdempotencyRecord existing = records.putIfAbsent(
        requestKey, IdempotencyRecord.inProgress(fingerprint));
    if (existing == null) {
      return Optional.empty();
    }
    if (!existing.fingerprint().equals(fingerprint)) {
      throw new IdempotencyKeyReusedException(requestKey);
    }
    return switch (existing.status()) {
      case COMPLETED -> Optional.of(existing.payment());
      case FAILED -> throw existing.failure();
      case IN_PROGRESS -> throw new PaymentRequestInProgressException(requestKey);
    };
  }

  /**
   * Marks a reserved key as completed and stores its replayable response.
   *
   * @param requestKey idempotency key
   * @param payment completed payment
   */
  public void complete(UUID requestKey, PaymentResponse payment) {
    records.computeIfPresent(requestKey, (key, record) ->
        record.status() == IdempotencyStatus.IN_PROGRESS ? record.completed(payment) : record);
  }

  /**
   * Stores a terminal failure so later retries replay it without calling the bank.
   *
   * @param requestKey idempotency key
   * @param failure terminal exception
   */
  public void fail(UUID requestKey, RuntimeException failure) {
    records.computeIfPresent(requestKey, (key, record) ->
        record.status() == IdempotencyStatus.IN_PROGRESS ? record.failed(failure) : record);
  }
}
