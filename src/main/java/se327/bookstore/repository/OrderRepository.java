package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se327.bookstore.entity.PurchaseOrder;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long> {
    Optional<PurchaseOrder> findByUserIdAndCheckoutKey(Long userId, UUID checkoutKey);
    Optional<PurchaseOrder> findByIdAndUserId(Long id, Long userId);
}
