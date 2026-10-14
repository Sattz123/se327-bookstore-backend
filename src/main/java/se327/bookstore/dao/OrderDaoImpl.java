package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.PurchaseOrder;
import se327.bookstore.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository @RequiredArgsConstructor
public class OrderDaoImpl implements OrderDao {
    private final OrderRepository repository;
    public List<PurchaseOrder> findByUserId(Long userId) { return repository.findByUserIdOrderByCreatedAtDescIdDesc(userId); }
    public Optional<PurchaseOrder> findByUserIdAndCheckoutKey(Long userId, UUID key) { return repository.findByUserIdAndCheckoutKey(userId, key); }
    public Optional<PurchaseOrder> findByIdAndUserId(Long id, Long userId) { return repository.findByIdAndUserId(id, userId); }
    public PurchaseOrder save(PurchaseOrder order) { return repository.saveAndFlush(order); }
}
