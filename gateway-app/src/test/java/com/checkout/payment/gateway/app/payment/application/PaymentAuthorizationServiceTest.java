package com.checkout.payment.gateway.app.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.app.payment.api.builder.CreatePaymentResponseBuilder;
import com.checkout.payment.gateway.app.payment.api.builder.PaymentResponseBuilder;
import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentStatus;
import com.checkout.payment.gateway.app.payment.routing.PaymentsConnectorRegistry;
import com.checkout.payment.gateway.app.payment.persistence.CardDataProtector;
import com.checkout.payment.gateway.app.payment.persistence.PaymentPersistenceMapper;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRepository;
import com.checkout.payment.gateway.app.payment.persistence.StoredPayment;
import com.checkout.payment.gateway.connector.api.IPaymentProcessor;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorizationServiceTest {

  private static final Instant NOW = Instant.parse("2025-06-15T10:00:00Z");
  private static final PaymentCommand COMMAND = new PaymentCommand(
      "2222405343248877", YearMonth.of(2030, 6), "GBP", 100, "123");

  @Mock
  private PaymentsConnectorRegistry connectorRegistry;
  @Mock
  private IPaymentProcessor processor;
  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private Clock clock;
  @Spy
  private PaymentResponseBuilder commonResponseBuilder = new PaymentResponseBuilder();
  @Spy
  private CreatePaymentResponseBuilder responseBuilder =
      new CreatePaymentResponseBuilder(commonResponseBuilder);
  @Spy
  private CardDataProtector cardDataProtector = new CardDataProtector();
  @Spy
  private PaymentPersistenceMapper persistenceMapper =
      new PaymentPersistenceMapper(cardDataProtector);
  @InjectMocks
  private PaymentAuthorizationService authorizationService;

  @Test
  void authorizesBuildsAndPersistsPayment() {
    CreatePaymentRequest request = request();
    when(connectorRegistry.get(BankId.BANK_SIMULATOR)).thenReturn(processor);
    when(processor.performPayments(COMMAND)).thenReturn(new PaymentOutcome(true, "auth-123"));
    when(clock.instant()).thenReturn(NOW);

    PaymentResponse payment = authorizationService.authorize(request, COMMAND);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(payment.getSource().getNumber()).isNull();
    assertThat(payment.getSource().getCardNumberLastFour()).isEqualTo("8877");
    assertThat(payment.getProcessing().getAuthCode()).isEqualTo("auth-123");
    assertThat(payment.getProcessedOn())
        .isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
    verify(paymentRepository).save(any(StoredPayment.class));
  }

  @Test
  void declinesWithoutProcessingDetails() {
    CreatePaymentRequest request = request();
    when(connectorRegistry.get(BankId.BANK_SIMULATOR)).thenReturn(processor);
    when(processor.performPayments(COMMAND)).thenReturn(new PaymentOutcome(false, ""));
    when(clock.instant()).thenReturn(NOW);

    PaymentResponse payment = authorizationService.authorize(request, COMMAND);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(payment.getProcessing()).isNull();
  }

  @Test
  void doesNotPersistWhenBankFails() {
    CreatePaymentRequest request = request();
    when(connectorRegistry.get(BankId.BANK_SIMULATOR)).thenReturn(processor);
    when(processor.performPayments(COMMAND))
        .thenThrow(new com.checkout.payment.gateway.connector.api.BankUnavailableException(
            "down", null));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> authorizationService.authorize(request, COMMAND))
        .isInstanceOf(com.checkout.payment.gateway.connector.api.BankUnavailableException.class);
    verify(paymentRepository, never()).save(any());
  }

  @Test
  void removesMaskedNumberForPostReplay() {
    PaymentResponse stored = new PaymentResponse();
    stored.setSource(new com.checkout.payment.gateway.app.payment.api.generated.model.PaymentSource());
    stored.getSource().setNumber("**** **** **** 8877");

    PaymentResponse replay = authorizationService.normalizeReplay(stored);

    assertThat(replay).isSameAs(stored);
    assertThat(replay.getSource().getNumber()).isNull();
  }

  private static CreatePaymentRequest request() {
    CardSource card = new CardSource("CREDIT_CARD", "2222405343248877", 6, 2030, "123");
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(card);
    request.setAmount(new Amount(100, "GBP"));
    return request;
  }
}
