package se327.bookstore.dao;

import se327.bookstore.entity.PurchaseOrder;
import java.util.Optional;
import java.util.UUID;

public interface OrderDao {
    Optional<PurchaseOrder> findByUserIdAndCheckoutKey(Long userId, UUID key);
    Optional<PurchaseOrder> findByIdAndUserId(Long id, Long userId);
    PurchaseOrder save(PurchaseOrder order);
}
