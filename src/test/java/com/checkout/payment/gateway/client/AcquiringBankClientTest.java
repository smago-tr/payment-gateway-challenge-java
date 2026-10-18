package com.checkout.payment.gateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankErrorException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AcquiringBankClientTest {

  private static final String BANK_URL = "http://localhost:8080";
  private static final String AUTHORIZED_BODY =
      "{\"authorized\": true, \"authorization_code\": \"0bb07405-6d44-4b50-a14f-7ae0beff13ad\"}";
  private static final String DECLINED_BODY =
      "{\"authorized\": false, \"authorization_code\": \"\"}";

  private final RestTemplate restTemplate = new RestTemplate();
  private final MockRestServiceServer bank = MockRestServiceServer.bindTo(restTemplate).build();
  private final AcquiringBankClient client = new AcquiringBankClient(restTemplate, BANK_URL);

  @Test
  void whenBankAuthorizesThenStatusIsAuthorized() {
    bank.expect(requestTo(BANK_URL + "/payments"))
        .andRespond(withSuccess(AUTHORIZED_BODY, MediaType.APPLICATION_JSON));

    assertEquals(PaymentStatus.AUTHORIZED, client.authorize(payment()));
  }

  @Test
  void whenBankDeclinesThenStatusIsDeclined() {
    bank.expect(requestTo(BANK_URL + "/payments"))
        .andRespond(withSuccess(DECLINED_BODY, MediaType.APPLICATION_JSON));

    assertEquals(PaymentStatus.DECLINED, client.authorize(payment()));
  }

  @Test
  void whenPaymentIsSentThenBankRequestHasBankFormat() {
    bank.expect(requestTo(BANK_URL + "/payments"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.card_number").value("4111111111111111"))
        .andExpect(jsonPath("$.expiry_date").value("04/2027"))
        .andExpect(jsonPath("$.currency").value("GBP"))
        .andExpect(jsonPath("$.amount").value(1050))
        .andExpect(jsonPath("$.cvv").value("012"))
        .andRespond(withSuccess(AUTHORIZED_BODY, MediaType.APPLICATION_JSON));

    client.authorize(payment());

    bank.verify();
  }

  @Test
  void whenBankRejectsTheRequestThenBankErrorIsThrown() {
    bank.expect(requestTo(BANK_URL + "/payments"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"error_message\": \"Not all required properties were sent in the request\"}"));

    assertThrows(BankErrorException.class, () -> client.authorize(payment()));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "not json", ""})
  void whenBankResponseIsUnusableThenBankErrorIsThrown(String body) {
    bank.expect(requestTo(BANK_URL + "/payments"))
        .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

    assertThrows(BankErrorException.class, () -> client.authorize(payment()));
  }

  private PostPaymentRequest payment() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setCardNumber("4111111111111111");
    request.setExpiryMonth(4);
    request.setExpiryYear(2027);
    request.setCurrency("GBP");
    request.setAmount(1050);
    request.setCvv("012");
    return request;
  }
}
