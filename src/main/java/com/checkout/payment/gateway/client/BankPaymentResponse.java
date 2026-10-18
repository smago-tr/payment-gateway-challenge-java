package com.checkout.payment.gateway.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record BankPaymentResponse(Boolean authorized) {

}
