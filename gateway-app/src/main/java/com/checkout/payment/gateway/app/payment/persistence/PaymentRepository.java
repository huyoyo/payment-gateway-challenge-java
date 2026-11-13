package com.checkout.payment.gateway.app.payment.persistence;

import java.util.Optional;
import java.util.UUID;

/**
 * Storage port for encrypted payments.
 */
public interface PaymentRepository {

  /**
   * Persists an already-encrypted payment model.
   *
   * @param payment encrypted storage model
   */
  void save(StoredPayment payment);

  /**
   * Loads a masked payment read model.
   *
   * @param id payment id
   * @return masked read model, or empty
   */
  Optional<PaymentRecord> findById(UUID id);
}
