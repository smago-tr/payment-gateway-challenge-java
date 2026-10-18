package com.checkout.payment.gateway.controller;


import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankErrorException;
import com.checkout.payment.gateway.exception.BankTimeoutException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
  @MockBean
  private AcquiringBankClient bankClient;

  @BeforeEach
  void bankAuthorizesByDefault() {
    when(bankClient.authorize(any())).thenReturn(PaymentStatus.AUTHORIZED);
  }

  @Test
  void whenPaymentWithIdExistThenCorrectPaymentIsReturned() throws Exception {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setAmount(10);
    payment.setCurrency("USD");
    payment.setStatus(PaymentStatus.AUTHORIZED);
    payment.setExpiryMonth(12);
    payment.setExpiryYear(2024);
    payment.setCardNumberLastFour("4321");

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
        .andExpect(jsonPath("$.message").value("Payment not found"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-uuid", "123", "12345678-1234-1234-1234-12345678901g"})
  void whenPaymentIdIsNotAValidUuidThen400IsReturned(String id) throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/payment/" + id))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Invalid payment id"));
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

  // -------------------- Validate expiry is in the future ---------------------------

  @Test
  void whenCardHasExpiredThenRejectedWithExpiryMonthError() throws Exception {
    mvc.perform(postPaymentWith("expiry_year", 2000))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("Payment rejected"))
        .andExpect(jsonPath("$.errors[*].field").value(contains("expiry_month")))
        .andExpect(jsonPath("$.errors[0].message").value("expiry date must be in the future"));
  }

  // -------------------- Process payment ---------------------------

  @Test
  void whenBankAuthorizesThenPaymentIsReturnedWithoutFullCardDetails() throws Exception {
    mvc.perform(postPaymentWith("card_number", "4111111111111111"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("1111"))
        .andExpect(jsonPath("$.expiryMonth").value(12))
        .andExpect(jsonPath("$.expiryYear").value(2099))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(100))
        .andExpect(jsonPath("$.cardNumber").doesNotExist())
        .andExpect(jsonPath("$.cvv").doesNotExist());
  }

  @Test
  void whenBankDeclinesThenPaymentIsReturnedAsDeclined() throws Exception {
    when(bankClient.authorize(any())).thenReturn(PaymentStatus.DECLINED);

    mvc.perform(postPaymentWith("card_number", "4111111111111112"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("Declined"));
  }

  @Test
  void whenCardNumberEndsInZerosThenLastFourKeepsLeadingZeros() throws Exception {
    mvc.perform(postPaymentWith("card_number", "4111111111110042"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cardNumberLastFour").value("0042"));
  }

  @Test
  void whenPaymentIsProcessedThenItCanBeRetrievedById() throws Exception {
    String created = mvc.perform(postPaymentWith("card_number", "4111111111111111"))
        .andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(created, "$.id");

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.status").value("Authorized"))
        .andExpect(jsonPath("$.cardNumberLastFour").value("1111"));
  }

  static Stream<Arguments> bankFailures() {
    return Stream.of(
        Arguments.of(new BankUnavailableException("down", null), 502,
            "The bank is unavailable, please try again in a few minutes"),
        Arguments.of(new BankTimeoutException("slow", null), 504,
            "The bank did not respond in time"),
        Arguments.of(new BankErrorException("bad request"), 500,
            "Could not process the payment"));
  }

  @ParameterizedTest
  @MethodSource("bankFailures")
  void whenBankFailsThenMerchantGetsMatchingErrorWithoutBankDetails(RuntimeException failure,
      int expectedStatus, String expectedMessage) throws Exception {
    when(bankClient.authorize(any())).thenThrow(failure);

    mvc.perform(postPaymentWith("card_number", "4111111111111111"))
        .andExpect(status().is(expectedStatus))
        .andExpect(jsonPath("$.message").value(expectedMessage));
  }

  // -------------------- Strict parsing ---------------------------

  static Stream<Arguments> wronglyTypedFields() {
    return Stream.of(
        Arguments.of("amount", 10.5),
        Arguments.of("amount", 3_000_000_000L),
        Arguments.of("amount", "100"),
        Arguments.of("expiry_month", "12"),
        Arguments.of("expiry_year", 2099.5),
        Arguments.of("cvv", 123),
        Arguments.of("card_number", 4111111111111111L));
  }

  @ParameterizedTest(name = "{0} = {1}")
  @MethodSource("wronglyTypedFields")
  void whenFieldHasWrongTypeThenPaymentIsRejected(String field, Object value) throws Exception {
    mvc.perform(postPaymentWith(field, value))
        .andExpect(status().isBadRequest());
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
