package com.checkout.payment.gateway.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.checkout.payment.gateway.exception.PaymentRejectedException;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import com.checkout.payment.gateway.repository.PaymentsRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentGatewayServiceTest {

  private final PaymentsRepository repository = mock(PaymentsRepository.class);
  private final ExpiryDateValidator expiryDateValidator = mock(ExpiryDateValidator.class);
  private final PaymentGatewayService service =
      new PaymentGatewayService(repository, expiryDateValidator);

  @Test
  void whenProcessingPaymentThenExpiryDateIsValidated() {
    PostPaymentRequest request = new PostPaymentRequest();

    service.processPayment(request);

    verify(expiryDateValidator).validate(request);
  }

  @Test
  void whenExpiryDateIsRejectedThenPaymentIsNotStored() {
    PostPaymentRequest request = new PostPaymentRequest();
    doThrow(new PaymentRejectedException(List.of()))
        .when(expiryDateValidator).validate(request);

    assertThrows(PaymentRejectedException.class, () -> service.processPayment(request));

    verifyNoInteractions(repository);
  }
}
