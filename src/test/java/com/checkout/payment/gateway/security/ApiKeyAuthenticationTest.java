package com.checkout.payment.gateway.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest(properties = "gateway.api-key=test-key")
@AutoConfigureMockMvc
class ApiKeyAuthenticationTest {

  @Autowired
  private MockMvc mvc;
  @MockBean
  private AcquiringBankClient bankClient;

  @Test
  void whenApiKeyIsMissingThenPostPaymentIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Missing or invalid API key"));
  }

  @Test
  void whenApiKeyIsWrongThenPostPaymentIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("X-API-Key", "wrong-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("Missing or invalid API key"));
  }

  @Test
  void whenApiKeyIsMissingThenGetPaymentIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void whenApiKeyIsCorrectThenRequestReachesTheController() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .header("X-API-Key", "test-key")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void whenCallingHealthThenNoApiKeyIsNeeded() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/actuator/health"))
        .andExpect(status().isOk());
  }
}
