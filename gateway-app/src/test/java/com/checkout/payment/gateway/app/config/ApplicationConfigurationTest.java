package com.checkout.payment.gateway.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class ApplicationConfigurationTest {

  @Test
  void providesUtcClock() {
    Clock clock = new ApplicationConfiguration().clock();

    assertThat(clock).isNotNull();
    assertThat(clock.getZone()).isEqualTo(java.time.ZoneOffset.UTC);
  }
}
