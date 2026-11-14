package com.checkout.payment.gateway.connector.bank;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import javax.ws.rs.client.Client;
import org.junit.jupiter.api.Test;

class BankSimulatorConfigurationTest {

  private final BankSimulatorConfiguration configuration = new BankSimulatorConfiguration();

  @Test
  void buildsClientWithDefaultTimeouts() {
    Client client = configuration.bankJerseyClient(new BankSimulatorProperties());
    try {
      assertThat(client).isNotNull();
    } finally {
      client.close();
    }
  }

  @Test
  void handlesNullAndNegativeTimeouts() {
    BankSimulatorProperties properties = new BankSimulatorProperties();
    properties.setConnectTimeout(null);
    properties.setReadTimeout(Duration.ofSeconds(-1));

    Client client = configuration.bankJerseyClient(properties);
    try {
      assertThat(client).isNotNull();
    } finally {
      client.close();
    }
  }

  @Test
  void capsLargeTimeouts() {
    BankSimulatorProperties properties = new BankSimulatorProperties();
    properties.setConnectTimeout(Duration.ofDays(365));
    properties.setReadTimeout(Duration.ofDays(365));

    Client client = configuration.bankJerseyClient(properties);
    try {
      assertThat(client).isNotNull();
    } finally {
      client.close();
    }
  }
}
