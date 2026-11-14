package com.checkout.payment.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OpenApiComponentsTest {

  @Test
  void configuresOpenApiMetadata() {
    OpenAPI openApi = new OpenApiConfiguration().paymentGatewayOpenApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("Payment Gateway API");
    assertThat(openApi.getInfo().getVersion()).isEqualTo("1.1.0");
    assertThat(openApi.getInfo().getDescription()).contains("bankId");
  }

  @Test
  void servesTheSourceOpenApiContract() throws IOException {
    var resource = new OpenApiSpecController().openApiSpec();

    assertThat(resource.exists()).isTrue();
    try (var input = resource.getInputStream()) {
      assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8))
          .contains("openapi: 3.0.3");
    }
  }
}
