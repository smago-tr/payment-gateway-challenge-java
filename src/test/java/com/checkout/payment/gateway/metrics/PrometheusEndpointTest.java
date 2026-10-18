package com.checkout.payment.gateway.metrics;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(properties = "gateway.api-key=test-key")
@AutoConfigureMockMvc
@AutoConfigureObservability
class PrometheusEndpointTest {

  @Autowired
  private MockMvc mvc;
  @MockBean
  private AcquiringBankClient bankClient;

  @Test
  void whenPaymentIsProcessedThenPrometheusExposesTheCounterByStatus() throws Exception {
    when(bankClient.authorize(any())).thenReturn(PaymentStatus.DECLINED);
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"card_number": "4111111111111112", "expiry_month": 12, "expiry_year": 2099,
                 "currency": "GBP", "amount": 100, "cvv": "123"}"""))
        .andExpect(status().isOk());

    mvc.perform(MockMvcRequestBuilders.get("/actuator/prometheus"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("payments_processed_total{")))
        .andExpect(content().string(containsString("status=\"DECLINED\"")));
  }
}
