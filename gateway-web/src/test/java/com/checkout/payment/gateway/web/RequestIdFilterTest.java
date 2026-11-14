package com.checkout.payment.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  void echoesSafeRequestIdAndCleansUpMdc() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payments");
    request.addHeader(RequestIdFilter.HEADER_NAME, "merchant-request_123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("merchant-request_123");
      ((MockHttpServletResponse) servletResponse).setStatus(202);
    });

    assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo("merchant-request_123");
    assertThat(response.getStatus()).isEqualTo(202);
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }

  @Test
  void generatesRequestIdForUnsafeOrMissingHeader() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payments");
    request.addHeader(RequestIdFilter.HEADER_NAME, "unsafe\nrequest-id");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

    String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);
    assertThat(UUID.fromString(requestId)).isNotNull();
    assertThat(requestId).isNotEqualTo("unsafe\nrequest-id");
  }

  @Test
  void removesMdcValueWhenFilterChainThrows() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/payments");
    request.addHeader(RequestIdFilter.HEADER_NAME, "request-123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(() -> filter.doFilter(request, response,
        (servletRequest, servletResponse) -> {
          throw new ServletException("downstream failure");
        }))
        .isInstanceOf(ServletException.class)
        .hasMessage("downstream failure");
    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
  }
}
