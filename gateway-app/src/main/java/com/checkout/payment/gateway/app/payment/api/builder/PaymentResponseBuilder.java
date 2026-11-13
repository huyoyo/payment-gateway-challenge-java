package com.checkout.payment.gateway.app.payment.api.builder;

import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentProcessing;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentStatus;
import com.checkout.payment.gateway.app.payment.api.generated.model.Source;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared response mapper used by the POST and GET response builders.
 */
@Component("gateway.app.api.builder.payment-response-builder")
public class PaymentResponseBuilder {

  /**
   * Builds the base POST response without a card number.
   */
  public PaymentResponse build(UUID id, BankId bankId, CreatePaymentRequest request,
      PaymentOutcome outcome, OffsetDateTime processedOn) {
    PaymentResponse response = new PaymentResponse();
    response.setId(id);
    response.setBankId(bankId);
    response.setStatus(outcome.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
    response.setAmount(amount(request.getAmount()));
    response.setSource(source(request.getSource()));
    response.setProcessedOn(processedOn);
    response.setProcessing(processing(outcome));
    return response;
  }

  /**
   * Adds the masked card number to a stored response in place.
   */
  public PaymentResponse withMaskedNumber(PaymentResponse response, String maskedCardNumber,
      String cardNumberLastFour) {
    PaymentSource source = response.getSource();
    if (source != null) {
      source.setNumber(maskedCardNumber);
      source.setCardNumberLastFour(cardNumberLastFour);
    }
    return response;
  }

  /**
   * Removes a masked card number added by a previous GET, for POST replay.
   */
  public PaymentResponse withoutCardNumber(PaymentResponse response) {
    if (response.getSource() != null) {
      response.getSource().setNumber(null);
    }
    return response;
  }

  private static PaymentSource source(Source requestSource) {
    PaymentSource responseSource = new PaymentSource();
    if (requestSource instanceof CardSource card) {
      responseSource.setType(PaymentSource.TypeEnum.CREDIT_CARD);
      responseSource.setBrand(card.getBrand());
      String lastFour = lastFour(card.getNumber());
      responseSource.setCardNumberLastFour(lastFour);
      responseSource.setExpiryMonth(card.getExpiryMonth());
      responseSource.setExpiryYear(card.getExpiryYear());
    }
    return responseSource;
  }

  private static Amount amount(Amount amount) {
    return amount == null ? null : new Amount(amount.getValue(), amount.getCurrency());
  }

  private static PaymentProcessing processing(PaymentOutcome outcome) {
    if (outcome.authorizationCode() == null || outcome.authorizationCode().isBlank()) {
      return null;
    }
    PaymentProcessing processing = new PaymentProcessing();
    processing.setAuthCode(outcome.authorizationCode());
    return processing;
  }

  private static String lastFour(String cardNumber) {
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
