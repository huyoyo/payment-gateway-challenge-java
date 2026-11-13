package com.checkout.payment.gateway.app.repository;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

/**
 * Thread-safe in-memory adapter. Card number and CVV are encrypted before insertion; reads decrypt
 * only the card number needed to build the masked GET response.
 */
@Repository("gateway.app.repository.in-memory-payment-repository")
public class InMemoryPaymentRepository implements PaymentRepository {

  private final Map<UUID, StoredPayment> payments = new ConcurrentHashMap<>();
  private final PaymentCryptoService cryptoService;

  public InMemoryPaymentRepository(PaymentCryptoService cryptoService) {
    this.cryptoService = cryptoService;
  }

  @Override
  public void save(PaymentResponse payment, String cardNumber, String cvv) {
    UUID id = payment.getId();
    payments.put(id, new StoredPayment(
        payment,
        cryptoService.encrypt(id, cardNumber),
        cryptoService.encrypt(id, cvv)));
  }

  @Override
  public Optional<PaymentRecord> findById(UUID id) {
    StoredPayment stored = payments.get(id);
    if (stored == null) {
      return Optional.empty();
    }
    String cardNumber = cryptoService.decrypt(id, stored.encryptedCardNumber());
    return Optional.of(new PaymentRecord(stored.payment(), cardNumber));
  }
}
