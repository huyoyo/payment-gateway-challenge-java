package com.checkout.payment.gateway.app.api.builder;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import org.springframework.stereotype.Component;

/**
 * Response builder for GET /payment/{id}. It reuses the common builder and enriches the stored
 * response in place with the masked card number.
 */
@Component("gateway.app.api.builder.get-payment-response-builder")
public class GetPaymentResponseBuilder {

  private final PaymentResponseBuilder responseBuilder;

  public GetPaymentResponseBuilder(PaymentResponseBuilder responseBuilder) {
    this.responseBuilder = responseBuilder;
  }

  /**
   * Adds the masked card number to the stored response without a deep copy.
   *
   * @param stored stored payment response
   * @param cardNumber decrypted card number, never returned directly
   * @return the same response instance with masked number and last four populated
   */
  public PaymentResponse build(PaymentResponse stored, String cardNumber) {
    return responseBuilder.withMaskedNumber(stored, cardNumber);
  }
}
