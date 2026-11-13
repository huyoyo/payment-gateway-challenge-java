package com.checkout.payment.gateway.app.api.builder;

import com.checkout.payment.gateway.app.api.generated.model.Amount;
import com.checkout.payment.gateway.app.api.generated.model.BankId;
import com.checkout.payment.gateway.app.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.api.generated.model.PaymentProcessing;
import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.api.generated.model.PaymentSource;
import com.checkout.payment.gateway.app.api.generated.model.PaymentStatus;
import com.checkout.payment.gateway.app.api.generated.model.Source;
import com.checkout.payment.gateway.app.api.generated.model.WalletSource;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Shared response mapper used by the POST and GET response builders. It never performs a deep copy
 * for GET enrichment; masking is applied to the supplied response source.
 */
@Component("gateway.app.api.builder.payment-response-builder")
public class PaymentResponseBuilder {

  /**
   * Builds the base POST response. The source intentionally has no full or masked card number;
   * only non-sensitive card details are included.
   *
   * @param id payment id
   * @param bankId selected connector
   * @param request public payment request
   * @param outcome acquiring-bank result
   * @param processedOn processing timestamp
   * @return base payment response
   */
  public PaymentResponse build(UUID id, BankId bankId, CreatePaymentRequest request,
      PaymentOutcome outcome, OffsetDateTime processedOn) {
    PaymentResponse response = new PaymentResponse();
    response.setId(id);
    response.setBankId(bankId);
    response.setStatus(outcome.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED);
    response.setAmount(copyAmount(request.getAmount()));
    response.setSource(buildSource(request.getSource()));
    response.setProcessedOn(processedOn);
    response.setProcessing(buildProcessing(outcome));
    return response;
  }

  /**
   * Adds the masked card number to a stored response in place. The GET builder intentionally avoids
   * a deep copy and delegates this shared mapping to this builder.
   *
   * @param response stored payment response to enrich
   * @param cardNumber decrypted card number, never returned directly
   * @return the same response instance with masked number and last four populated
   */
  public PaymentResponse withMaskedNumber(PaymentResponse response, String cardNumber) {
    PaymentSource source = response.getSource();
    if (source != null) {
      String lastFour = lastFour(cardNumber);
      source.setNumber(mask(lastFour));
      source.setCardNumberLastFour(lastFour);
    }
    return response;
  }

  /**
   * Removes a previously added masked card number from a response in place. Used when a completed
   * payment is replayed through POST after a GET has enriched the stored response.
   *
   * @param response stored payment response
   * @return the same response instance without a masked card number
   */
  public PaymentResponse withoutCardNumber(PaymentResponse response) {
    PaymentSource source = response.getSource();
    if (source != null) {
      source.setNumber(null);
    }
    return response;
  }

  private static String mask(String lastFour) {
    return "**** **** **** " + lastFour;
  }

  private static PaymentSource buildSource(Source source) {
    PaymentSource responseSource = new PaymentSource();
    if (source instanceof CardSource card) {
      responseSource.setType(PaymentSource.TypeEnum.CREDIT_CARD);
      responseSource.setBrand(card.getBrand());
      String lastFour = lastFour(card.getNumber());
      responseSource.setCardNumberLastFour(lastFour);
      responseSource.setExpiryMonth(card.getExpiryMonth());
      responseSource.setExpiryYear(card.getExpiryYear());
    } else if (source instanceof WalletSource wallet) {
      responseSource.setType(PaymentSource.TypeEnum.WALLET);
      if (wallet.getWalletType() != null) {
        responseSource.setWalletType(
            PaymentSource.WalletTypeEnum.fromValue(wallet.getWalletType().getValue()));
      }
    }
    return responseSource;
  }

  private static Amount copyAmount(Amount amount) {
    return amount == null ? null : new Amount(amount.getValue(), amount.getCurrency());
  }

  private static PaymentProcessing buildProcessing(PaymentOutcome outcome) {
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
