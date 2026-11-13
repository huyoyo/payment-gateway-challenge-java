package com.checkout.payment.gateway.app.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.app.api.generated.model.BankId;
import com.checkout.payment.gateway.connector.api.IPaymentProcessor;
import com.checkout.payment.gateway.connector.api.error.UnsupportedBankException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentsConnectorRegistryTest {

  @Test
  void resolvesConnectorByBankId() {
    IPaymentProcessor simulator = processor("bank-simulator");
    PaymentsConnectorRegistry registry = new PaymentsConnectorRegistry(List.of(simulator));

    assertThat(registry.get(BankId.BANK_SIMULATOR)).isSameAs(simulator);
  }

  @Test
  void rejectsMissingBankAndListsSupportedBanks() {
    PaymentsConnectorRegistry registry =
        new PaymentsConnectorRegistry(List.of(processor("bank-simulator")));

    assertThatThrownBy(() -> registry.get(null))
        .isInstanceOf(UnsupportedBankException.class)
        .hasMessageContaining("bank-simulator")
        .hasMessageNotContaining("null");
  }

  @Test
  void rejectsDuplicateBankIds() {
    assertThatThrownBy(() -> new PaymentsConnectorRegistry(
        List.of(processor("bank-simulator"), processor("bank-simulator"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Duplicate");
  }

  @Test
  void rejectsEmptyConnectorList() {
    assertThatThrownBy(() -> new PaymentsConnectorRegistry(List.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No IPaymentProcessor connectors");
  }

  @Test
  void rejectsBlankBankId() {
    assertThatThrownBy(() -> new PaymentsConnectorRegistry(List.of(processor("  "))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must not be blank");
  }

  @Test
  void rejectsConnectorBankIdNotInApiEnum() {
    assertThatThrownBy(() -> new PaymentsConnectorRegistry(List.of(processor("bank-other"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unsupported bank connector id: bank-other");
  }

  private static IPaymentProcessor processor(String bankId) {
    IPaymentProcessor processor = mock(IPaymentProcessor.class);
    when(processor.getBankId()).thenReturn(bankId);
    return processor;
  }
}
