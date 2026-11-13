package com.checkout.payment.gateway.connector.bank;

import com.checkout.payment.gateway.connector.api.IPaymentProcessor;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;

import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentRequest;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Bank simulator connector implementing the application's {@link IPaymentProcessor} abstraction.
 */
@Component("gateway.connector.bank.bank-payments-connector")
public class BankPaymentsConnector implements IPaymentProcessor {

  private final BankSimulatorRequestBuilder requestBuilder;
  private final BankSimulatorResponseBuilder responseBuilder;
  private final BankSimulatorClient client;
  private final BankSimulatorProperties properties;

  public BankPaymentsConnector(BankSimulatorRequestBuilder requestBuilder,
      BankSimulatorResponseBuilder responseBuilder, BankSimulatorClient client,
      BankSimulatorProperties properties) {
    if (!StringUtils.hasText(properties.getBankId())) {
      throw new IllegalStateException("connector.bank.simulator.bank-id must be configured");
    }
    this.requestBuilder = requestBuilder;
    this.responseBuilder = responseBuilder;
    this.client = client;
    this.properties = properties;
  }

  @Override
  public String getBankId() {
    return properties.getBankId().trim();
  }

  @Override
  public PaymentOutcome performPayments(PaymentCommand command) {
    BankPaymentRequest request = requestBuilder.build(command);
    BankPaymentResponse response = client.post(request);
    return responseBuilder.build(response);
  }
}
