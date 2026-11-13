package com.checkout.payment.gateway.app.api.builder;

import com.checkout.payment.gateway.app.api.generated.model.BankId;
import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Response builder for POST /payments. A freshly created payment must not expose a card number in
 * the response source.
 */
@Component("gateway.app.api.builder.create-payment-response-builder")
public class CreatePaymentResponseBuilder {

  private final PaymentResponseBuilder responseBuilder;

  public CreatePaymentResponseBuilder(PaymentResponseBuilder responseBuilder) {
    this.responseBuilder = responseBuilder;
  }

  /**
   * Builds the POST payment response without a card number field.
   *
   * @param id payment id
   * @param bankId selected connector
   * @param request public payment request
   * @param outcome acquiring-bank result
   * @param processedOn processing timestamp
   * @return response for the create operation
   */
  public PaymentResponse build(UUID id, BankId bankId, CreatePaymentRequest request,
      PaymentOutcome outcome, OffsetDateTime processedOn) {
    return responseBuilder.build(id, bankId, request, outcome, processedOn);
  }

  /**
   * Normalizes a replayed completed payment to the POST representation by removing any masked card
   * number that may have been added by a previous GET.
   *
   * @param stored stored completed response
   * @return the same response instance without a card number
   */
  public PaymentResponse buildReplay(PaymentResponse stored) {
    return responseBuilder.withoutCardNumber(stored);
  }
}
