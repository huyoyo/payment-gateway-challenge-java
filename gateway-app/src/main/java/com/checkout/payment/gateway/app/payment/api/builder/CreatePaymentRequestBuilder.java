package com.checkout.payment.gateway.app.payment.api.builder;

import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.error.PaymentRejectedException;
import com.checkout.payment.gateway.connector.api.error.ValidationError;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Converts the generated public request into the connector-neutral payment command.
 */
@Component("gateway.app.api.builder.create-payment-request-builder")
public class CreatePaymentRequestBuilder {

  /**
   * Maps the public request to the connector command.
   *
   * @param request public payment request
   * @return neutral payment command
   */
  public PaymentCommand build(CreatePaymentRequest request) {
    if (!(request.getSource() instanceof CardSource card)) {
      throw new PaymentRejectedException(List.of(new ValidationError("source.type",
          "must be CREDIT_CARD")));
    }
    return new PaymentCommand(
        card.getNumber().trim(),
        YearMonth.of(card.getExpiryYear(), card.getExpiryMonth()),
        request.getAmount().getCurrency().trim().toUpperCase(Locale.ROOT),
        request.getAmount().getValue(),
        card.getCvv());
  }
}
