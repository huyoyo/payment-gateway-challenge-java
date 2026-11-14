package com.checkout.payment.gateway.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration("gateway.web.open-api-configuration")
public class OpenApiConfiguration {

  @Bean
  public OpenAPI paymentGatewayOpenApi() {
    return new OpenAPI().info(new Info()
        .title("Payment Gateway API")
        .description("Accepts card payments, routes them to the connector selected by the body "
            + "bankId field and exposes stored payment details for reconciliation.")
        .version("1.1.0"));
  }
}
