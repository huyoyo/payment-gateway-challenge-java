package com.checkout.payment.gateway.connector.bank;

import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Builds the generated bank simulator request POJO from the neutral payment command.
 */
@Component("gateway.connector.bank.bank-simulator-request-builder")
public class BankSimulatorRequestBuilder {

  /**
   * Maps the neutral command to the bank simulator request.
   *
   * @param command payment command
   * @return generated bank request
   */
  public BankPaymentRequest build(PaymentCommand command) {
    BankPaymentRequest request = new BankPaymentRequest();
    request.setCardNumber(command.cardNumber());
    request.setExpiryDate(String.format(Locale.ROOT, "%02d/%04d",
        command.expiryDate().getMonthValue(), command.expiryDate().getYear()));
    request.setCurrency(command.currency());
    request.setAmount(command.amount());
    request.setCvv(command.cvv());
    return request;
  }
}
