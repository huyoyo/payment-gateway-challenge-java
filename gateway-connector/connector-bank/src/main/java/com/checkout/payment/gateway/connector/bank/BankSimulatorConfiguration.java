package com.checkout.payment.gateway.connector.bank;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import java.time.Duration;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientProperties;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jersey client configuration exposed as a Spring-managed singleton.
 */
@Configuration("gateway.connector.bank.bank-simulator-configuration")
public class BankSimulatorConfiguration {

  @Bean(destroyMethod = "close")
  public Client bankJerseyClient(BankSimulatorProperties properties) {
    ClientConfig config = new ClientConfig();
    config.property(ClientProperties.CONNECT_TIMEOUT,
        timeoutMillis(properties.getConnectTimeout()));
    config.property(ClientProperties.READ_TIMEOUT,
        timeoutMillis(properties.getReadTimeout()));
    return ClientBuilder.newClient(config).register(JacksonJsonProvider.class);
  }

  private static int timeoutMillis(Duration timeout) {
    long millis = timeout == null ? 0 : timeout.toMillis();
    return (int) Math.min(Integer.MAX_VALUE, Math.max(0, millis));
  }
}
