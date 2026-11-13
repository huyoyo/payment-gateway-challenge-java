package com.checkout.payment.gateway.app.payment.persistence;

import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Explicit persistence boundary: transforms a transient command into encrypted storage data and
 * turns stored data into a masked read model.
 */
@Component("gateway.app.payment.persistence.payment-persistence-mapper")
@RequiredArgsConstructor
public class PaymentPersistenceMapper {

  private final CardDataProtector cardDataProtector;

  /**
   * Encrypts only the PAN. CVV is intentionally not persisted.
   *
   * @param payment public payment response
   * @param command transient connector command
   * @return encrypted storage model
   */
  public StoredPayment toStored(PaymentResponse payment, PaymentCommand command) {
    return new StoredPayment(payment,
        cardDataProtector.encrypt(payment.getId(), command.cardNumber()));
  }

  /**
   * Decrypts the PAN in memory only to derive the masked read model.
   *
   * @param stored encrypted storage model
   * @return masked payment read model
   */
  public PaymentRecord toRecord(StoredPayment stored) {
    String cardNumber = cardDataProtector.decrypt(
        stored.payment().getId(), stored.encryptedPan());
    String lastFour = lastFour(cardNumber);
    return new PaymentRecord(stored.payment(), "**** **** **** " + lastFour, lastFour);
  }

  private static String lastFour(String cardNumber) {
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
