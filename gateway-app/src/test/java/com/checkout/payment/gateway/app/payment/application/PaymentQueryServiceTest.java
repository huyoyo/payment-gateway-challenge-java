package com.checkout.payment.gateway.app.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.app.payment.api.builder.GetPaymentResponseBuilder;
import com.checkout.payment.gateway.app.payment.api.builder.PaymentResponseBuilder;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentSource;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRecord;
import com.checkout.payment.gateway.app.payment.persistence.PaymentRepository;
import com.checkout.payment.gateway.connector.api.error.PaymentNotFoundException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

  @Mock
  private PaymentRepository paymentRepository;
  @Spy
  private PaymentResponseBuilder commonResponseBuilder = new PaymentResponseBuilder();
  @Spy
  private GetPaymentResponseBuilder responseBuilder =
      new GetPaymentResponseBuilder(commonResponseBuilder);
  @InjectMocks
  private PaymentQueryService paymentQueryService;

  @Test
  void loadsAndMasksCardNumber() {
    UUID id = UUID.randomUUID();
    PaymentResponse stored = new PaymentResponse();
    stored.setId(id);
    PaymentSource source = new PaymentSource();
    source.setCardNumberLastFour("8877");
    stored.setSource(source);
    when(paymentRepository.findById(id))
        .thenReturn(Optional.of(new PaymentRecord(stored, "**** **** **** 8877", "8877")));

    PaymentResponse response = paymentQueryService.getPayment(id);

    assertThat(response.getSource().getNumber()).isEqualTo("**** **** **** 8877");
    assertThat(response.getSource().getCardNumberLastFour()).isEqualTo("8877");
  }

  @Test
  void rejectsUnknownPayment() {
    UUID id = UUID.randomUUID();
    when(paymentRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentQueryService.getPayment(id))
        .isInstanceOf(PaymentNotFoundException.class);
  }
}
