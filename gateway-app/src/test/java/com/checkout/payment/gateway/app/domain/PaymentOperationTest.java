package com.checkout.payment.gateway.app.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.app.api.builder.CreatePaymentRequestBuilder;
import com.checkout.payment.gateway.app.api.builder.CreatePaymentResponseBuilder;
import com.checkout.payment.gateway.app.api.builder.GetPaymentResponseBuilder;
import com.checkout.payment.gateway.app.api.builder.PaymentResponseBuilder;
import com.checkout.payment.gateway.app.api.generated.model.Amount;
import com.checkout.payment.gateway.app.api.generated.model.BankId;
import com.checkout.payment.gateway.app.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.api.generated.model.PaymentSource;
import com.checkout.payment.gateway.app.api.generated.model.PaymentStatus;
import com.checkout.payment.gateway.app.connector.PaymentsConnectorRegistry;
import com.checkout.payment.gateway.app.idempotency.PaymentIdempotencyService;
import com.checkout.payment.gateway.app.idempotency.PaymentRequestFingerprint;
import com.checkout.payment.gateway.app.repository.PaymentRecord;
import com.checkout.payment.gateway.app.repository.PaymentRepository;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import com.checkout.payment.gateway.connector.api.IPaymentProcessor;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import com.checkout.payment.gateway.connector.api.error.PaymentNotFoundException;
import com.checkout.payment.gateway.connector.api.error.PaymentRejectedException;
import com.checkout.payment.gateway.connector.api.error.UnsupportedBankException;
import com.checkout.payment.gateway.connector.api.error.ValidationError;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentOperationTest {

  private static final Instant FIXED_INSTANT = Instant.parse("2025-06-15T10:00:00Z");
  private static final OffsetDateTime PROCESSED_ON =
      OffsetDateTime.ofInstant(FIXED_INSTANT, ZoneOffset.UTC);
  private static final BankId BANK_ID = BankId.BANK_SIMULATOR;
  private static final String CARD_NUMBER = "2222405343248877";
  private static final UUID REQUEST_KEY = UUID.randomUUID();

  @Mock
  private PaymentValidator paymentValidator;
  @Mock
  private PaymentsConnectorRegistry paymentsConnectorRegistry;
  @Mock
  private IPaymentProcessor paymentProcessor;
  @Mock
  private PaymentRepository paymentRepository;
  @Mock
  private Clock clock;
  @Mock
  private PaymentRequestFingerprint requestFingerprint;
  @Spy
  private CreatePaymentRequestBuilder createPaymentRequestBuilder =
      new CreatePaymentRequestBuilder();
  @Spy
  private PaymentResponseBuilder commonResponseBuilder = new PaymentResponseBuilder();
  @Spy
  private CreatePaymentResponseBuilder createResponseBuilder =
      new CreatePaymentResponseBuilder(commonResponseBuilder);
  @Spy
  private GetPaymentResponseBuilder getResponseBuilder =
      new GetPaymentResponseBuilder(commonResponseBuilder);
  @Spy
  private PaymentIdempotencyService idempotencyService = new PaymentIdempotencyService();
  @InjectMocks
  private PaymentOperation paymentOperation;

  @Test
  void persistsAuthorizedPaymentAndRoutesByBankId() {
    CreatePaymentRequest request = request("GBP");
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(paymentsConnectorRegistry.get(BANK_ID)).thenReturn(paymentProcessor);
    when(paymentProcessor.performPayments(any(PaymentCommand.class)))
        .thenReturn(new PaymentOutcome(true, "auth-123"));
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");
    when(clock.instant()).thenReturn(FIXED_INSTANT);

    PaymentResponse payment = paymentOperation.perform(request);

    assertThat(payment.getId()).isNotNull();
    assertThat(payment.getBankId()).isEqualTo(BANK_ID);
    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    assertThat(payment.getAmount().getValue()).isEqualTo(100);
    assertThat(payment.getAmount().getCurrency()).isEqualTo("GBP");
    assertThat(payment.getSource().getType()).isEqualTo(PaymentSource.TypeEnum.CREDIT_CARD);
    assertThat(payment.getSource().getCardNumberLastFour()).isEqualTo("8877");
    assertThat(payment.getSource().getNumber()).isNull();
    assertThat(payment.getSource().getExpiryMonth()).isEqualTo(8);
    assertThat(payment.getSource().getExpiryYear()).isEqualTo(2025);
    assertThat(payment.getProcessedOn()).isEqualTo(PROCESSED_ON);
    assertThat(payment.getProcessing().getAuthCode()).isEqualTo("auth-123");

    ArgumentCaptor<PaymentCommand> command = ArgumentCaptor.forClass(PaymentCommand.class);
    verify(paymentProcessor).performPayments(command.capture());
    assertThat(command.getValue().cardNumber()).isEqualTo(CARD_NUMBER);
    assertThat(command.getValue().expiryDate()).isEqualTo(YearMonth.of(2025, 8));
    assertThat(command.getValue().currency()).isEqualTo("GBP");
    assertThat(command.getValue().amount()).isEqualTo(100);
    assertThat(command.getValue().cvv()).isEqualTo("123");
    verify(paymentRepository).save(payment, CARD_NUMBER, "123");
  }

  @Test
  void persistsDeclinedPayment() {
    CreatePaymentRequest request = request("GBP");
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(paymentsConnectorRegistry.get(BANK_ID)).thenReturn(paymentProcessor);
    when(paymentProcessor.performPayments(any(PaymentCommand.class)))
        .thenReturn(new PaymentOutcome(false, ""));
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");
    when(clock.instant()).thenReturn(FIXED_INSTANT);

    PaymentResponse payment = paymentOperation.perform(request);

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.DECLINED);
    assertThat(payment.getProcessing()).isNull();
    verify(paymentRepository).save(payment, CARD_NUMBER, "123");
  }

  @Test
  void replaysCompletedRequestWithoutCallingBankAgain() {
    CreatePaymentRequest request = request("GBP");
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(paymentsConnectorRegistry.get(BANK_ID)).thenReturn(paymentProcessor);
    when(paymentProcessor.performPayments(any(PaymentCommand.class)))
        .thenReturn(new PaymentOutcome(true, "auth-123"));
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");
    when(clock.instant()).thenReturn(FIXED_INSTANT);

    PaymentResponse first = paymentOperation.perform(request);
    PaymentResponse second = paymentOperation.perform(request);

    assertThat(second).isSameAs(first);
    verify(paymentProcessor, times(1)).performPayments(any(PaymentCommand.class));
    verify(paymentRepository, times(1)).save(first, CARD_NUMBER, "123");
  }

  @Test
  void replaysTerminalFailureWithoutCallingBankAgain() {
    CreatePaymentRequest request = request("GBP");
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(paymentsConnectorRegistry.get(BANK_ID)).thenReturn(paymentProcessor);
    when(paymentProcessor.performPayments(any(PaymentCommand.class)))
        .thenThrow(new BankUnavailableException("bank down", null));
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");

    assertThatThrownBy(() -> paymentOperation.perform(request))
        .isInstanceOf(BankUnavailableException.class);
    assertThatThrownBy(() -> paymentOperation.perform(request))
        .isInstanceOf(BankUnavailableException.class);

    verify(paymentProcessor, times(1)).performPayments(any(PaymentCommand.class));
    verify(paymentRepository, never()).save(any(), anyString(), anyString());
  }

  @Test
  void replaysUnexpectedFailureWithoutCallingBankAgain() {
    CreatePaymentRequest request = request("GBP");
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(paymentsConnectorRegistry.get(BANK_ID)).thenReturn(paymentProcessor);
    when(paymentProcessor.performPayments(any(PaymentCommand.class)))
        .thenReturn(new PaymentOutcome(true, "auth-123"));
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");
    when(clock.instant()).thenReturn(FIXED_INSTANT);
    doThrow(new IllegalStateException("database timeout"))
        .when(paymentRepository).save(any(), anyString(), anyString());

    assertThatThrownBy(() -> paymentOperation.perform(request))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> paymentOperation.perform(request))
        .isInstanceOf(IllegalStateException.class);

    verify(paymentProcessor, times(1)).performPayments(any(PaymentCommand.class));
    verify(paymentRepository, times(1)).save(any(), anyString(), anyString());
  }

  @Test
  void rejectsInvalidRequestWithoutRoutingOrPersisting() {
    CreatePaymentRequest request = request("GBP");
    List<ValidationError> errors =
        List.of(new ValidationError("source.expiryYear", "Card expiry date must be in the future"));
    when(paymentValidator.validate(request)).thenReturn(errors);

    assertThatThrownBy(() -> paymentOperation.perform(request))
        .isInstanceOfSatisfying(PaymentRejectedException.class,
            exception -> assertThat(exception.getErrors()).isEqualTo(errors));

    verifyNoInteractions(paymentsConnectorRegistry, paymentProcessor, paymentRepository);
  }

  @Test
  void rejectsMissingRequestKeyBeforeRouting() {
    CreatePaymentRequest request = request("GBP");
    request.setRequestKey(null);
    when(paymentValidator.validate(request)).thenReturn(List.of());

    assertThatThrownBy(() -> paymentOperation.perform(request))
        .isInstanceOfSatisfying(PaymentRejectedException.class,
            exception -> assertThat(exception.getErrors())
                .extracting(ValidationError::field).containsExactly("requestKey"));

    verifyNoInteractions(paymentsConnectorRegistry, paymentProcessor, paymentRepository);
  }

  @Test
  void propagatesUnsupportedBankWithoutPersisting() {
    CreatePaymentRequest request = requestWithBank(null);
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(paymentsConnectorRegistry.get(null))
        .thenThrow(new UnsupportedBankException(List.of(BANK_ID.getValue())));

    assertThatThrownBy(() -> paymentOperation.perform(request))
        .isInstanceOf(UnsupportedBankException.class)
        .hasMessageContaining(BANK_ID.getValue());

    verifyNoInteractions(paymentProcessor, paymentRepository);
  }

  @Test
  void returnsStoredPaymentWithMaskedNumber() {
    UUID id = UUID.randomUUID();
    PaymentResponse payment = new PaymentResponse();
    payment.setId(id);
    payment.setBankId(BANK_ID);
    payment.setStatus(PaymentStatus.AUTHORIZED);
    PaymentSource source = new PaymentSource();
    source.setType(PaymentSource.TypeEnum.CREDIT_CARD);
    source.setCardNumberLastFour("8877");
    payment.setSource(source);
    when(paymentRepository.findById(id))
        .thenReturn(Optional.of(new PaymentRecord(payment, CARD_NUMBER)));

    PaymentResponse response = paymentOperation.getPayment(id);

    assertThat(response.getId()).isEqualTo(id);
    assertThat(response.getSource().getNumber()).isEqualTo("**** **** **** 8877");
    assertThat(response.getSource().getCardNumberLastFour()).isEqualTo("8877");
  }

  @Test
  void throwsWhenPaymentDoesNotExist() {
    UUID id = UUID.randomUUID();
    when(paymentRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentOperation.getPayment(id))
        .isInstanceOf(PaymentNotFoundException.class)
        .hasMessageContaining(id.toString());
  }

  private static CreatePaymentRequest request(String currency) {
    return requestWithBankAndCurrency(BANK_ID, currency);
  }

  private static CreatePaymentRequest requestWithBank(BankId bankId) {
    return requestWithBankAndCurrency(bankId, "GBP");
  }

  private static CreatePaymentRequest requestWithBankAndCurrency(
      BankId bankId, String currency) {
    CardSource card = new CardSource("CREDIT_CARD",
        CARD_NUMBER, 8, 2025, "123");
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setRequestKey(REQUEST_KEY);
    request.setBankId(bankId);
    request.setSource(card);
    request.setAmount(new Amount(100, currency));
    return request;
  }
}
