package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se327.bookstore.entity.PaymentMethod;
import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {
    List<PaymentMethod> findByUserIdOrderByDefaultMethodDescCreatedAtDescIdDesc(Long userId);
    Optional<PaymentMethod> findByIdAndUserId(Long id, Long userId);
    Optional<PaymentMethod> findByUserIdAndDefaultMethodTrue(Long userId);
}
