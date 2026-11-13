package com.checkout.payment.gateway.app.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.app.payment.api.builder.CreatePaymentRequestBuilder;
import com.checkout.payment.gateway.app.payment.api.generated.model.Amount;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.domain.PaymentValidator;
import com.checkout.payment.gateway.app.payment.idempotency.PaymentIdempotencyService;
import com.checkout.payment.gateway.app.payment.idempotency.PaymentRequestFingerprint;
import com.checkout.payment.gateway.connector.api.BankUnavailableException;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.error.PaymentRejectedException;
import com.checkout.payment.gateway.connector.api.error.ValidationError;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentCreationServiceTest {

  private static final UUID REQUEST_KEY = UUID.randomUUID();
  private static final PaymentCommand COMMAND = new PaymentCommand(
      "2222405343248877", YearMonth.of(2030, 6), "GBP", 100, "123");

  @Mock
  private PaymentValidator paymentValidator;
  @Mock
  private CreatePaymentRequestBuilder requestBuilder;
  @Mock
  private PaymentRequestFingerprint requestFingerprint;
  @Mock
  private PaymentIdempotencyService idempotencyService;
  @Mock
  private PaymentAuthorizationService authorizationService;
  @InjectMocks
  private PaymentCreationService paymentCreationService;

  @Test
  void createsAndCompletesNewPayment() {
    CreatePaymentRequest request = request(REQUEST_KEY);
    PaymentResponse payment = new PaymentResponse();
    payment.setId(UUID.randomUUID());
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(requestBuilder.build(request)).thenReturn(COMMAND);
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");
    when(idempotencyService.start(REQUEST_KEY, "fingerprint")).thenReturn(Optional.empty());
    when(authorizationService.authorize(request, COMMAND)).thenReturn(payment);

    assertThat(paymentCreationService.perform(request)).isSameAs(payment);
    verify(idempotencyService).complete(REQUEST_KEY, payment);
    verify(idempotencyService, never()).fail(any(), any());
  }

  @Test
  void replaysCompletedPaymentWithoutAuthorizingAgain() {
    CreatePaymentRequest request = request(REQUEST_KEY);
    PaymentResponse stored = new PaymentResponse();
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(requestBuilder.build(request)).thenReturn(COMMAND);
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");
    when(idempotencyService.start(REQUEST_KEY, "fingerprint"))
        .thenReturn(Optional.of(stored));
    when(authorizationService.normalizeReplay(stored)).thenReturn(stored);

    assertThat(paymentCreationService.perform(request)).isSameAs(stored);
    verify(authorizationService, never()).authorize(any(), any());
  }

  @Test
  void recordsFailureWhenAuthorizationFails() {
    CreatePaymentRequest request = request(REQUEST_KEY);
    when(paymentValidator.validate(request)).thenReturn(List.of());
    when(requestBuilder.build(request)).thenReturn(COMMAND);
    when(requestFingerprint.fingerprint(request)).thenReturn("fingerprint");
    when(idempotencyService.start(REQUEST_KEY, "fingerprint")).thenReturn(Optional.empty());
    when(authorizationService.authorize(request, COMMAND))
        .thenThrow(new BankUnavailableException("down", null));

    assertThatThrownBy(() -> paymentCreationService.perform(request))
        .isInstanceOf(BankUnavailableException.class);
    verify(idempotencyService).fail(eq(REQUEST_KEY), any(BankUnavailableException.class));
  }

  @Test
  void rejectsValidationErrorsBeforeReservingKey() {
    CreatePaymentRequest request = request(REQUEST_KEY);
    List<ValidationError> errors = List.of(new ValidationError("source.number", "invalid"));
    when(paymentValidator.validate(request)).thenReturn(errors);

    assertThatThrownBy(() -> paymentCreationService.perform(request))
        .isInstanceOf(PaymentRejectedException.class);
    verifyNoInteractions(requestBuilder, idempotencyService, authorizationService);
  }

  @Test
  void rejectsMissingRequestKeyBeforeReservingKey() {
    CreatePaymentRequest request = request(null);
    when(paymentValidator.validate(request)).thenReturn(List.of());

    assertThatThrownBy(() -> paymentCreationService.perform(request))
        .isInstanceOf(PaymentRejectedException.class);
    verifyNoInteractions(requestBuilder, idempotencyService, authorizationService);
  }

  private static CreatePaymentRequest request(UUID requestKey) {
    CardSource card = new CardSource("CREDIT_CARD", "2222405343248877", 6, 2030, "123");
    CreatePaymentRequest request = new CreatePaymentRequest();
    request.setRequestKey(requestKey);
    request.setBankId(BankId.BANK_SIMULATOR);
    request.setSource(card);
    request.setAmount(new Amount(100, "GBP"));
    return request;
  }
}
