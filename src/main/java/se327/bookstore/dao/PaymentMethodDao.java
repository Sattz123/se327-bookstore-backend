package se327.bookstore.dao;

import se327.bookstore.entity.PaymentMethod;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodDao {
    List<PaymentMethod> findByUserId(Long userId);
    Optional<PaymentMethod> findByIdAndUserId(Long id, Long userId);
    PaymentMethod save(PaymentMethod method);
    void clearDefault(Long userId);
    void delete(PaymentMethod method);
}
