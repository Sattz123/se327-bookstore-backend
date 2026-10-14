package se327.bookstore.dao;

import se327.bookstore.entity.PurchaseOrder;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface OrderDao {
    List<PurchaseOrder> findByUserId(Long userId);
    Optional<PurchaseOrder> findByUserIdAndCheckoutKey(Long userId, UUID key);
    Optional<PurchaseOrder> findByIdAndUserId(Long id, Long userId);
    PurchaseOrder save(PurchaseOrder order);
}
