package com.checkout.payment.gateway.app.payment.api.builder;

import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Response builder for GET /payment/{id}. It reuses the common builder and enriches the stored
 * response in place with the masked card number.
 */
@Component("gateway.app.api.builder.get-payment-response-builder")
@RequiredArgsConstructor
public class GetPaymentResponseBuilder {

  private final PaymentResponseBuilder responseBuilder;

  /**
   * Adds the masked card number to the stored response without a deep copy.
   *
   * @param record masked persistence read model
   * @return the same response instance with masked number and last four populated
   */
  public PaymentResponse build(PaymentRecord record) {
    return responseBuilder.withMaskedNumber(
        record.payment(), record.maskedCardNumber(), record.lastFour());
  }
}
