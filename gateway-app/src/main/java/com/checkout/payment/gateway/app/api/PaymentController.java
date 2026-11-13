package com.checkout.payment.gateway.app.api;

import com.checkout.payment.gateway.app.api.generated.PaymentsApi;
import com.checkout.payment.gateway.app.api.generated.model.CreatePaymentRequest;
import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.domain.PaymentOperation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("gateway.app.api.payment-controller")
public class PaymentController implements PaymentsApi {

  @Autowired
  private PaymentOperation paymentOperation;

  @Override
  public ResponseEntity<PaymentResponse> createPayment(
      @Valid @RequestBody CreatePaymentRequest request) {
    PaymentResponse payment = paymentOperation.perform(request);
    return ResponseEntity
        .created(URI.create("/payment/" + payment.getId()))
        .body(payment);
  }

  @Override
  public ResponseEntity<PaymentResponse> getPayment(@PathVariable("id") UUID id) {
    return ResponseEntity.ok(paymentOperation.getPayment(id));
  }
}
