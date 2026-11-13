package com.checkout.payment.gateway.app.payment.application;

import com.checkout.payment.gateway.app.payment.api.builder.GetPaymentResponseBuilder;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRecord;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRepository;
import com.checkout.payment.gateway.connector.api.error.PaymentNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Reads a stored payment and maps it to the GET response.
 */
@Service("gateway.app.domain.payment-query-service")
@RequiredArgsConstructor
public class PaymentQueryService {

  private final PaymentRepository paymentRepository;
  private final GetPaymentResponseBuilder responseBuilder;

  /**
   * Loads a payment and adds its masked card number.
   *
   * @param id payment id
   * @return GET response
   */
  public PaymentResponse getPayment(UUID id) {
    PaymentRecord record = paymentRepository.findById(id)
        .orElseThrow(() -> new PaymentNotFoundException(id));
    return responseBuilder.build(record);
  }
}
