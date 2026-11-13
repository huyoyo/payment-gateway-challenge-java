package com.checkout.payment.gateway.app.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.checkout.payment.gateway.app.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.app.api.generated.model.PaymentSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryPaymentRepositoryTest {

  private final InMemoryPaymentRepository repository =
      new InMemoryPaymentRepository(new PaymentCryptoService());

  @Test
  void decryptsCardNumberOnReadAndReturnsOnlyMaskedValue() {
    UUID id = UUID.randomUUID();
    PaymentResponse payment = new PaymentResponse();
    payment.setId(id);
    PaymentSource source = new PaymentSource();
    source.setType(PaymentSource.TypeEnum.CREDIT_CARD);
    payment.setSource(source);

    repository.save(payment, "2222405343248877", "123");

    PaymentRecord loaded = repository.findById(id).orElseThrow();
    assertThat(loaded.cardNumber()).isEqualTo("2222405343248877");
    assertThat(loaded.payment().getSource().getNumber()).isNull();
  }
}
