package com.checkout.payment.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Executable Spring Boot entry point.
 *
 * <p>Component scanning starts at {@code com.checkout.payment.gateway}, so beans contributed by
 * {@code gateway-app} and {@code gateway-connector} are discovered without extra configuration.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PaymentGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(PaymentGatewayApplication.class, args);
  }

}
