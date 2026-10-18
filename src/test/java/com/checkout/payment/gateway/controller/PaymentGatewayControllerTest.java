package com.checkout.payment.gateway.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentGatewayControllerTest {

  @Autowired
  private MockMvc mvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  PaymentsRepository paymentsRepository;

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour(4321);

    paymentsRepository.add(payment);

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + payment.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(payment.getStatus().getName()))
        .andExpect(jsonPath("$.cardNumberLastFour").value(payment.getCardNumberLastFour()))
        .andExpect(jsonPath("$.expiryMonth").value(payment.getExpiryMonth()))
        .andExpect(jsonPath("$.expiryYear").value(payment.getExpiryYear()))
        .andExpect(jsonPath("$.currency").value(payment.getCurrency()))
        .andExpect(jsonPath("$.amount").value(payment.getAmount()));
  }

  @Test
  void whenPaymentWithIdDoesNotExistThen404IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Page not found"));
  }

  @Test
  void whenPaymentHasNoFieldsThenRejectedWithAllRequiredFieldsListed() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder(
            "card_number", "expiry_month", "expiry_year", "currency", "amount", "cvv")));
  }

  @Test
  void whenPaymentHasNoBodyThen400IsReturned() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").exists());
  }

  // -------------------- Validate card number ---------------------------

  @ParameterizedTest
  @ValueSource(strings = {
      "1234567890123",
      "12345678901234567890",
      "4111abcd11111111",
      " 4111111111111111",
      ""})
  void whenCardNumberIsInvalidThenRejectedWithCardNumberError(String cardNumber) throws Exception {
    mvc.perform(postPaymentWith("card_number", cardNumber))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors[*].field").value(contains("card_number")))
        .andExpect(jsonPath("$.errors[0].message").value("must be 14 to 19 digits"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"12345678901234", "1234567890123456789"})
  void whenCardNumberIsAtLengthBoundaryThenPaymentIsNotRejected(String cardNumber)
      throws Exception {
    mvc.perform(postPaymentWith("card_number", cardNumber))
        .andExpect(status().isOk());
  }

  // -------------------- Validate CVV ---------------------------

  @ParameterizedTest
  @ValueSource(strings = {"12", "12345", "12a", " 123", ""})
  void whenCvvIsInvalidThenRejectedWithCvvError(String cvv) throws Exception {
    mvc.perform(postPaymentWith("cvv", cvv))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors[*].field").value(contains("cvv")))
        .andExpect(jsonPath("$.errors[0].message").value("must be 3 to 4 digits"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"123", "1234", "012", "0012"})
  void whenCvvIsValidThenPaymentIsNotRejected(String cvv) throws Exception {
    mvc.perform(postPaymentWith("cvv", cvv))
        .andExpect(status().isOk());
  }

  // -------------------- Validate expiry month ---------------------------

  @ParameterizedTest
  @ValueSource(ints = {-1, 0, 13})
  void whenExpiryMonthIsOutOfRangeThenRejected(int month) throws Exception {
    mvc.perform(postPaymentWith("expiry_month", month))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors[*].field").value(contains("expiry_month")))
        .andExpect(jsonPath("$.errors[0].message").value("must be between 1 and 12"));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 12})
  void whenExpiryMonthIsAtRangeThenPaymentIsNotRejected(int month) throws Exception {
    mvc.perform(postPaymentWith("expiry_month", month))
        .andExpect(status().isOk());
  }

  // -------------------- Validate amount ---------------------------

  @ParameterizedTest
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
  void whenAmountIsNotPositiveThenRejectedWithAmountError(int amount) throws Exception {
    mvc.perform(postPaymentWith("amount", amount))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors[*].field").value(contains("amount")))
        .andExpect(jsonPath("$.errors[0].message").value("must be at least 1"));
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 1050, Integer.MAX_VALUE})
  void whenAmountIsPositiveThenPaymentIsNotRejected(int amount) throws Exception {
    mvc.perform(postPaymentWith("amount", amount))
        .andExpect(status().isOk());
  }

  // -------------------- Validate currency ---------------------------

  @ParameterizedTest
  @ValueSource(strings = {"JPY", "usd", "US", "USDD", " USD", ""})
  void whenCurrencyIsNotSupportedThenRejectedWithCurrencyError(String currency) throws Exception {
    mvc.perform(postPaymentWith("currency", currency))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors[*].field").value(contains("currency")))
        .andExpect(jsonPath("$.errors[0].message").value("must be one of GBP, EUR, USD"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"GBP", "EUR", "USD"})
  void whenCurrencyIsSupportedThenPaymentIsNotRejected(String currency) throws Exception {
    mvc.perform(postPaymentWith("currency", currency))
        .andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder postPaymentWith(String field, Object value)
      throws JsonProcessingException {
    Map<String, Object> payment = new HashMap<>(Map.of(
        "card_number", "4111111111111111",
        "expiry_month", 12,
        "expiry_year", 2099,
        "currency", "USD",
        "amount", 100,
        "cvv", "123"));
    payment.put(field, value);
    return MockMvcRequestBuilders.post("/payment")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(payment));
  }
}
