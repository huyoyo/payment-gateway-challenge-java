package com.checkout.payment.gateway.app.payment.application;

import com.checkout.payment.gateway.app.payment.api.builder.CreatePaymentResponseBuilder;
import com.checkout.payment.gateway.app.payment.api.generated.model.BankId;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.routing.PaymentsConnectorRegistry;
import com.checkout.payment.gateway.app.payment.persistence.PaymentPersistenceMapper;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRepository;
import com.checkout.payment.gateway.connector.api.IPaymentProcessor;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import com.checkout.payment.gateway.connector.api.PaymentOutcome;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Authorizes one payment with the selected bank connector and persists the result.
 */
@Service("gateway.app.domain.payment-authorization-service")
@RequiredArgsConstructor
public class PaymentAuthorizationService {

  private final PaymentsConnectorRegistry connectorRegistry;
  private final CreatePaymentResponseBuilder responseBuilder;
  private final PaymentRepository paymentRepository;
  private final PaymentPersistenceMapper persistenceMapper;
  private final Clock clock;

  /**
   * Calls the bank and stores an encrypted payment.
   *
   * @param request public request
   * @param command normalized connector command
   * @return created response
   */
  public PaymentResponse authorize(CreatePaymentRequest request, PaymentCommand command) {
    BankId bankId = request.getBankId();
    IPaymentProcessor processor = connectorRegistry.get(bankId);
    PaymentOutcome outcome = processor.performPayments(command);
    PaymentResponse payment = responseBuilder.build(
        UUID.randomUUID(), bankId, request, outcome,
        OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    paymentRepository.save(persistenceMapper.toStored(payment, command));
    return payment;
  }

  /**
   * Normalizes a stored response to the POST representation for idempotent replay.
   *
   * @param stored stored response
   * @return response without a card number
   */
  public PaymentResponse normalizeReplay(PaymentResponse stored) {
    return responseBuilder.buildReplay(stored);
  }
}
