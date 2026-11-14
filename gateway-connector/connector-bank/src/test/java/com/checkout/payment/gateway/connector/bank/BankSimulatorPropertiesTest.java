package com.checkout.payment.gateway.connector.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class BankSimulatorPropertiesTest {

  @Test
  void exposesDefaultsAndMutators() {
    BankSimulatorProperties properties = new BankSimulatorProperties();
    assertThat(properties.getBankId()).isEqualTo("bank-simulator");
    assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8080");

    properties.setBankId("bank-other");
    properties.setBaseUrl("http://bank:8080");
    properties.setConnectTimeout(Duration.ofSeconds(1));
    properties.setReadTimeout(Duration.ofSeconds(2));

    assertThat(properties.getBankId()).isEqualTo("bank-other");
    assertThat(properties.getBaseUrl()).isEqualTo("http://bank:8080");
    assertThat(properties.getConnectTimeout()).isEqualTo(Duration.ofSeconds(1));
    assertThat(properties.getReadTimeout()).isEqualTo(Duration.ofSeconds(2));
  }
}
