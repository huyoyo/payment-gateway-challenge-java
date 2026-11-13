package com.checkout.payment.gateway.app.payment.application;

import com.checkout.payment.gateway.app.payment.api.builder.CreatePaymentRequestBuilder;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.domain.PaymentValidator;
import com.checkout.payment.gateway.app.payment.idempotency.PaymentIdempotencyService;
import com.checkout.payment.gateway.app.payment.idempotency.PaymentRequestFingerprint;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.error.PaymentRejectedException;
import com.checkout.payment.gateway.connector.api.error.ValidationError;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creates a payment: validates, reserves the idempotency key, authorizes and records the outcome.
 */
@Service("gateway.app.domain.payment-creation-service")
@RequiredArgsConstructor
public class PaymentCreationService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentCreationService.class);

  private final PaymentValidator paymentValidator;
  private final CreatePaymentRequestBuilder requestBuilder;
  private final PaymentRequestFingerprint requestFingerprint;
  private final PaymentIdempotencyService idempotencyService;
  private final PaymentAuthorizationService authorizationService;

  /**
   * Creates a payment idempotently.
   *
   * @param request public payment request
   * @return POST response without a card number
   */
  public PaymentResponse perform(CreatePaymentRequest request) {
    List<ValidationError> validationErrors = paymentValidator.validate(request);
    if (!validationErrors.isEmpty()) {
      throw new PaymentRejectedException(validationErrors);
    }

    UUID requestKey = requestKeyOrThrow(request);
    PaymentCommand command = requestBuilder.build(request);
    String fingerprint = requestFingerprint.fingerprint(request);

    Optional<PaymentResponse> replay = idempotencyService.start(requestKey, fingerprint);
    if (replay.isPresent()) {
      LOG.info("Replaying completed payment {} for requestKey {}",
          replay.get().getId(), requestKey);
      return authorizationService.normalizeReplay(replay.get());
    }

    try {
      PaymentResponse payment = authorizationService.authorize(request, command);
      idempotencyService.complete(requestKey, payment);
      LOG.info("Processed payment {} via bank {} with status {}",
          payment.getId(), request.getBankId().getValue(), payment.getStatus());
      return payment;
    } catch (RuntimeException ex) {
      idempotencyService.fail(requestKey, ex);
      throw ex;
    }
  }

  private static UUID requestKeyOrThrow(CreatePaymentRequest request) {
    UUID requestKey = request.getRequestKey();
    if (requestKey == null) {
      throw new PaymentRejectedException(List.of(
          new ValidationError("requestKey", "must not be null")));
    }
    return requestKey;
  }
}
