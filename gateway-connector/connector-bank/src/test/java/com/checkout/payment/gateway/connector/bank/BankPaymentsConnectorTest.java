package com.checkout.payment.gateway.connector.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentRequest;
import com.checkout.payment.gateway.connector.bank.generated.model.BankPaymentResponse;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BankPaymentsConnectorTest {

  @Mock
  private BankSimulatorRequestBuilder requestBuilder;
  @Mock
  private BankSimulatorResponseBuilder responseBuilder;
  @Mock
  private BankSimulatorClient client;

  private BankSimulatorProperties properties;
  private BankPaymentsConnector connector;

  @BeforeEach
  void setUp() {
    properties = new BankSimulatorProperties();
    properties.setBankId("bank-simulator");
    connector = new BankPaymentsConnector(requestBuilder, responseBuilder, client, properties);
  }

  @Test
  void exposesConfiguredBankId() {
    assertThat(connector.getBankId()).isEqualTo("bank-simulator");
  }

  @Test
  void buildsCallsAndMapsThroughClient() {
    PaymentCommand command = new PaymentCommand(
        "2222405343248877", YearMonth.of(2025, 8), "GBP", 100, "123");
    BankPaymentRequest wireRequest = new BankPaymentRequest();
    BankPaymentResponse wireResponse = new BankPaymentResponse();
    PaymentOutcome outcome = new PaymentOutcome(true, "auth-123");

    when(requestBuilder.build(command)).thenReturn(wireRequest);
    when(client.post(wireRequest)).thenReturn(wireResponse);
    when(responseBuilder.build(wireResponse)).thenReturn(outcome);

    assertThat(connector.performPayments(command)).isEqualTo(outcome);
    verify(requestBuilder).build(command);
    verify(client).post(wireRequest);
    verify(responseBuilder).build(wireResponse);
  }
}
