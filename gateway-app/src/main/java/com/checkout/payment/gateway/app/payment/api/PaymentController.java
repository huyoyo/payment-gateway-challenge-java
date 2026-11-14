package com.checkout.payment.gateway.app.payment.api;

import com.checkout.payment.gateway.app.payment.api.generated.PaymentsApi;
import com.checkout.payment.gateway.app.payment.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.application.PaymentCreationService;
import com.checkout.payment.gateway.app.payment.application.PaymentQueryService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("gateway.app.api.payment-controller")
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

  private final PaymentCreationService paymentCreationService;
  private final PaymentQueryService paymentQueryService;

  @Override
  public ResponseEntity<PaymentResponse> createPayment(
      @Valid @RequestBody CreatePaymentRequest request) {
    PaymentResponse payment = paymentCreationService.perform(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(payment);
  }

  @Override
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(paymentQueryService.getPayment(id));
  }
}
