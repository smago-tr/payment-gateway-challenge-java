package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankErrorException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AcquiringBankClient {

  private static final Logger LOG = LoggerFactory.getLogger(AcquiringBankClient.class);

  private final RestTemplate restTemplate;
  private final String bankUrl;

  public AcquiringBankClient(RestTemplate restTemplate, @Value("${bank.url}") String bankUrl) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
  }

  public PaymentStatus authorize(PostPaymentRequest request) {
    BankPaymentResponse response = post(BankPaymentRequest.mapToBankRequest(request));
    LOG.info("Calling Bank for payment processing");

    if (response == null || response.authorized() == null) {
      throw new BankErrorException("Bank response did not say whether the payment was authorized");
    }
    return response.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
  }

  private BankPaymentResponse post(BankPaymentRequest request) {
    try {
      return restTemplate.postForObject(bankUrl + "/payments", request, BankPaymentResponse.class);
    } catch (RestClientException e) {
      throw new BankErrorException("Bank call failed", e);
    }
  }
}
