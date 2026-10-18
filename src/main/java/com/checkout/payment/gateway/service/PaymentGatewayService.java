package com.checkout.payment.gateway.service;

import com.checkout.payment.gateway.client.AcquiringBankClient;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.checkout.payment.gateway.exception.EventProcessingException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.model.PostPaymentResponse;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

  private static final Logger LOG = LoggerFactory.getLogger(PaymentGatewayService.class);

  private final PaymentsRepository paymentsRepository;
  private final ExpiryDateValidator expiryDateValidator;
  private final AcquiringBankClient bankClient;

  public PaymentGatewayService(PaymentsRepository paymentsRepository,
      ExpiryDateValidator expiryDateValidator, AcquiringBankClient bankClient) {
    this.paymentsRepository = paymentsRepository;
    this.expiryDateValidator = expiryDateValidator;
    this.bankClient = bankClient;
  }

  public PostPaymentResponse getPaymentById(UUID id) {
    LOG.debug("Requesting access to to payment with ID {}", id);
    return paymentsRepository.get(id).orElseThrow(() -> new EventProcessingException("Invalid ID"));
  }

  public PostPaymentResponse processPayment(PostPaymentRequest paymentRequest) {
    expiryDateValidator.validate(paymentRequest);
    PaymentStatus status = bankClient.authorize(paymentRequest);

    PostPaymentResponse payment = toPayment(paymentRequest, status);
    paymentsRepository.add(payment);
    LOG.info("Payment {} processed with status {}", payment.getId(), status);
    return payment;
  }

  private PostPaymentResponse toPayment(PostPaymentRequest request, PaymentStatus status) {
    PostPaymentResponse payment = new PostPaymentResponse();
    payment.setId(UUID.randomUUID());
    payment.setStatus(status);
    payment.setCardNumberLastFour(lastFour(request.getCardNumber()));
    payment.setExpiryMonth(request.getExpiryMonth());
    payment.setExpiryYear(request.getExpiryYear());
    payment.setCurrency(request.getCurrency());
    payment.setAmount(request.getAmount());
    return payment;
  }

  private String lastFour(String cardNumber) {
    return cardNumber.substring(cardNumber.length() - 4);
  }
}
