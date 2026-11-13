package com.checkout.payment.gateway.app.domain;

import com.checkout.payment.gateway.app.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.connector.api.error.ValidationError;
import java.time.Clock;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Validates only the cross-field rule that OpenAPI/Bean Validation cannot express: for a card
 * source, the expiry month/year combination must not be in the past. Field presence, formats,
 * ranges and currency pattern are enforced by the generated OpenAPI models.
 */
@Component("gateway.app.domain.payment-validator")
public class PaymentValidator {

  private final Clock clock;

  public PaymentValidator(Clock clock) {
    this.clock = clock;
  }

  /**
   * Validates the card expiry cross-field rule.
   *
   * @param request payment request
   * @return validation errors, empty when valid
   */
  public List<ValidationError> validate(CreatePaymentRequest request) {
    if (request == null || !(request.getSource() instanceof CardSource card)) {
      return List.of();
    }
    if (card.getExpiryMonth() == null || card.getExpiryYear() == null) {
      return List.of();
    }
    YearMonth expiry = YearMonth.of(card.getExpiryYear(), card.getExpiryMonth());
    if (expiry.isBefore(YearMonth.now(clock))) {
      return List.of(new ValidationError("source.expiryYear",
          "Card expiry date must be in the future"));
    }
    return List.of();
  }
}
