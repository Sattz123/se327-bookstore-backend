package se327.bookstore.service;

import se327.bookstore.dto.PaymentMethodDto;
import se327.bookstore.dto.PaymentMethodRequest;
import java.util.List;

public interface PaymentMethodService {
    List<PaymentMethodDto> getPaymentMethods(String email);
    PaymentMethodDto addPaymentMethod(String email, PaymentMethodRequest request);
    PaymentMethodDto setDefault(String email, Long id);
    void deletePaymentMethod(String email, Long id);
}
