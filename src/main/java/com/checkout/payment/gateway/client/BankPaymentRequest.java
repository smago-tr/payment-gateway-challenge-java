package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;

record BankPaymentRequest(
    @JsonProperty("card_number")
    String cardNumber,
    @JsonProperty("expiry_date")
    String expiryDate,
    String currency,
    Integer amount,
    String cvv) {

  static BankPaymentRequest mapToBankRequest(PostPaymentRequest request) {
    return new BankPaymentRequest(
        request.getCardNumber(),
        expiryDate(request.getExpiryMonth() ,request.getExpiryYear()),
      request.getCurrency(),
      request.getAmount(),
      request.getCvv());
  }

  private static String expiryDate(Integer expiryMonth, Integer expiryYear){
    return String.format("%02d/%d", expiryMonth, expiryYear);
  }

}
