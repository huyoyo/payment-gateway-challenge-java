package com.checkout.payment.gateway.app.domain;

import com.checkout.payment.gateway.app.api.builder.CreatePaymentRequestBuilder;
import com.checkout.payment.gateway.app.api.builder.CreatePaymentResponseBuilder;
import com.checkout.payment.gateway.app.api.builder.GetPaymentResponseBuilder;
import com.checkout.payment.gateway.app.api.generated.model.BankId;
import com.checkout.payment.gateway.app.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.connector.PaymentsConnectorRegistry;
import com.checkout.payment.gateway.app.idempotency.PaymentIdempotencyService;
import com.checkout.payment.gateway.app.idempotency.PaymentRequestFingerprint;
import com.checkout.payment.gateway.app.repository.PaymentRecord;
import com.checkout.payment.gateway.app.repository.PaymentRepository;
import com.checkout.payment.gateway.connector.api.IPaymentProcessor;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import com.checkout.payment.gateway.connector.api.error.PaymentNotFoundException;
import com.checkout.payment.gateway.connector.api.error.PaymentRejectedException;
import com.checkout.payment.gateway.connector.api.error.ValidationError;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Application operation for the payment use cases.
 *
 * <p>The operation owns {@link #perform(CreatePaymentRequest)} and the bank-id routing. It selects
 * the {@link IPaymentProcessor} for the supplied bank id, invokes the uniform SPI method, uses the
 * public request/response builders and stores the generated response.
 */
@Service("gateway.app.domain.payment-operation")
public class PaymentOperation {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentOperation.class);

  @Autowired
  private PaymentValidator paymentValidator;

  @Autowired
  private PaymentsConnectorRegistry paymentsConnectorRegistry;

  @Autowired
  private PaymentRepository paymentRepository;

  @Autowired
  private CreatePaymentRequestBuilder createPaymentRequestBuilder;

  @Autowired
  private CreatePaymentResponseBuilder createPaymentResponseBuilder;

  @Autowired
  private GetPaymentResponseBuilder getPaymentResponseBuilder;

  @Autowired
  private Clock clock;

  @Autowired
  private PaymentIdempotencyService idempotencyService;

  @Autowired
  private PaymentRequestFingerprint requestFingerprint;

  /**
   * Validates, routes, invokes the acquiring bank and persists an idempotent payment.
   *
   * @param request public payment request
   * @return POST response without a card number field
   */
  public PaymentResponse perform(@Nonnull CreatePaymentRequest request) {
    List<ValidationError> validationErrors = paymentValidator.validate(request);
    if (!validationErrors.isEmpty()) {
      throw new PaymentRejectedException(validationErrors);
    }
    if (request.getRequestKey() == null) {
      throw new PaymentRejectedException(List.of(
          new ValidationError("requestKey", "must not be null")));
    }

    BankId bankId = request.getBankId();
    IPaymentProcessor paymentProcessor = paymentsConnectorRegistry.get(bankId);
    PaymentCommand command = createPaymentRequestBuilder.build(request);
    String fingerprint = requestFingerprint.fingerprint(request);

    Optional<PaymentResponse> replay = idempotencyService.start(
        request.getRequestKey(), fingerprint);
    if (replay.isPresent()) {
      LOG.info("Replaying completed payment {} for requestKey {}",
          replay.get().getId(), request.getRequestKey());
      return createPaymentResponseBuilder.buildReplay(replay.get());
    }

    try {
      PaymentOutcome outcome = paymentProcessor.performPayments(command);
      PaymentResponse payment = createPaymentResponseBuilder.build(
          UUID.randomUUID(), bankId, request, outcome,
          OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
      CardSource card = (CardSource) request.getSource();
      paymentRepository.save(payment, card.getNumber(), card.getCvv());
      idempotencyService.complete(request.getRequestKey(), payment);
      LOG.info("Processed payment {} via bank {} with status {}",
          payment.getId(), payment.getBankId().getValue(), payment.getStatus());
      return payment;
    } catch (RuntimeException ex) {
      idempotencyService.fail(request.getRequestKey(), ex);
      throw ex;
    }
  }

  /**
   * Loads a payment and maps it to the GET response, including the masked card number.
   *
   * @param id payment id
   * @return GET response with masked card number
   */
  public PaymentResponse getPayment(UUID id) {
    PaymentRecord record = paymentRepository.findById(id)
        .orElseThrow(() -> new PaymentNotFoundException(id));
    return getPaymentResponseBuilder.build(record.payment(), record.cardNumber());
  }
}
