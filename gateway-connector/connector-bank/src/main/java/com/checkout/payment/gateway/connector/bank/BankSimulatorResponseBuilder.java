package com.checkout.payment.gateway.connector.bank;

import com.checkout.payment.gateway.connector.api.BankIntegrationException;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentResponse;
import org.springframework.stereotype.Component;

/**
 * Maps the generated bank simulator response POJO to the neutral payment outcome.
 */
@Component("gateway.connector.bank.bank-simulator-response-builder")
public class BankSimulatorResponseBuilder {

  /**
   * Maps the generated bank response to the neutral outcome.
   *
   * @param response bank response
   * @return payment outcome
   */
  public PaymentOutcome build(BankPaymentResponse response) {
    if (response == null || response.getAuthorized() == null) {
      throw new BankIntegrationException("Acquiring bank returned an unreadable response");
    }
    return new PaymentOutcome(response.getAuthorized(), response.getAuthorizationCode());
  }
}
