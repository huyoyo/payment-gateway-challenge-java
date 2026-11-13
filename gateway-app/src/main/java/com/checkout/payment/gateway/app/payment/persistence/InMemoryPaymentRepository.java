package com.checkout.payment.gateway.app.payment.persistence;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * In-memory encrypted payment store.
 */
@Repository("gateway.app.payment.persistence.in-memory-payment-repository")
@RequiredArgsConstructor
public class InMemoryPaymentRepository implements PaymentRepository {

  private final Map<UUID, StoredPayment> payments = new ConcurrentHashMap<>();
  private final PaymentPersistenceMapper persistenceMapper;

  @Override
  public void save(StoredPayment payment) {
    Objects.requireNonNull(payment, "payment must not be null");
    Objects.requireNonNull(payment.payment().getId(), "payment id must not be null");
    payments.put(payment.payment().getId(), payment);
  }

  @Override
  public Optional<PaymentRecord> findById(UUID id) {
    Objects.requireNonNull(id, "id must not be null");
    StoredPayment stored = payments.get(id);
    return stored == null ? Optional.empty() : Optional.of(persistenceMapper.toRecord(stored));
  }
}
