package com.checkout.payment.gateway.app.repository;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage port for payments.
 */
public interface PaymentRepository {

  /**
   * Persists a payment and encrypts the supplied card number and CVV.
   *
   * @param payment public payment response to persist
   * @param cardNumber full card number
   * @param cvv card verification value
   */
  void save(PaymentResponse payment, String cardNumber, String cvv);

  /**
   * Loads a payment and decrypts its card number in memory for response mapping.
   *
   * @param id payment id
   * @return payment read model, or empty when not found
   */
  Optional<PaymentRecord> findById(UUID id);
}
