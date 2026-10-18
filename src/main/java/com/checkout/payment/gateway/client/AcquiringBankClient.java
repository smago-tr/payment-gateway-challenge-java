package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.BankErrorException;
import com.checkout.payment.gateway.exception.BankTimeoutException;
import com.checkout.payment.gateway.exception.BankUnavailableException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AcquiringBankClient {

  private static final Logger LOG = LoggerFactory.getLogger(AcquiringBankClient.class);

  private static final int MAX_ATTEMPTS = 3;

  private final RestTemplate restTemplate;
  private final String bankUrl;
  private final Duration retryBackoff;

  public AcquiringBankClient(RestTemplate restTemplate, @Value("${bank.url}") String bankUrl,
      @Value("${bank.retry-backoff:200ms}") Duration retryBackoff) {
    this.restTemplate = restTemplate;
    this.bankUrl = bankUrl;
    this.retryBackoff = retryBackoff;
  }

  public PaymentStatus authorize(PostPaymentRequest request) {
    BankPaymentResponse response = post(BankPaymentRequest.mapToBankRequest(request));
    LOG.info ("Calling Bank for payment processing");

    if (response == null || response.authorized() == null) {
      throw new BankErrorException("Bank response did not say whether the payment was authorized");
    }
    return response.authorized() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
  }

  private BankPaymentResponse post(BankPaymentRequest request) {
    Duration backoff = retryBackoff;
    for (int attempt = 1; ; attempt++) {
      try {
        return restTemplate.postForObject(bankUrl + "/payments", request,
            BankPaymentResponse.class);
      } catch (RestClientException e) {
        if (isTimeout(e)) {
          throw new BankTimeoutException("Bank call timed out", e);
        }
        if (!isSafeToRetry(e)) {
          throw new BankErrorException("Bank call failed", e);
        }
        if (attempt == MAX_ATTEMPTS) {
          throw new BankUnavailableException("Bank unavailable after " + attempt + " attempts", e);
        }
        LOG.warn("Bank unavailable on attempt {}, retrying in {}", attempt, backoff);
        pause(backoff, e);
        backoff = backoff.multipliedBy(2);
      }
    }
  }

  // A timeout may mean the bank processed the request, so only failures that
  // certainly did not reach it are retried
  private boolean isSafeToRetry(RestClientException e) {
    return e instanceof HttpServerErrorException.ServiceUnavailable
        || (e instanceof ResourceAccessException && e.getCause() instanceof ConnectException);
  }

  private boolean isTimeout(RestClientException e) {
    return e instanceof ResourceAccessException && e.getCause() instanceof SocketTimeoutException;
  }

  private void pause(Duration backoff, RestClientException cause) {
    try {
      Thread.sleep(backoff.toMillis());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new BankUnavailableException("Interrupted while waiting to retry the bank", cause);
    }
  }
}
