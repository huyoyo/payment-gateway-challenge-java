package com.checkout.payment.gateway.app.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.checkout.payment.gateway.app.payment.api.generated.model.CardSource;
import com.checkout.payment.gateway.app.payment.api.generated.model.PaymentResponse;
import com.checkout.payment.gateway.connector.api.PaymentCommand;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryPaymentRepositoryTest {

  private final CardDataProtector protector = new CardDataProtector();
  private final PaymentPersistenceMapper mapper = new PaymentPersistenceMapper(protector);
  private final InMemoryPaymentRepository repository = new InMemoryPaymentRepository(mapper);

  @Test
  void savesEncryptedPanAndReadsOnlyMaskedData() {
    UUID id = UUID.randomUUID();
    PaymentResponse payment = new PaymentResponse();
    payment.setId(id);
    CardSource source = new CardSource("CREDIT_CARD", "2222405343248877", 12, 2099, "123");
    source.setNumber(null);
    payment.setSource(source);

    repository.save(mapper.toStored(payment, command("2222405343248877")));

    PaymentRecord loaded = repository.findById(id).orElseThrow();
    assertThat(loaded.maskedCardNumber()).isEqualTo("**** **** **** 8877");
    assertThat(loaded.lastFour()).isEqualTo("8877");
    assertThat(((CardSource) loaded.payment().getSource()).getNumber()).isNull();
  }

  @Test
  void returnsEmptyForUnknownId() {
    assertThat(repository.findById(UUID.randomUUID())).isEmpty();
  }

  @Test
  void rejectsNullSaveAndNullId() {
    assertThatThrownBy(() -> repository.save(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> repository.findById(null))
        .isInstanceOf(NullPointerException.class);
  }

  private static PaymentCommand command(String pan) {
    return new PaymentCommand(pan, YearMonth.of(2030, 6), "GBP", 100, "123");
  }
}
