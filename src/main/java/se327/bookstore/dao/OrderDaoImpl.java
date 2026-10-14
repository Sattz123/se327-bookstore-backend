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
    public se327.bookstore.repository.DashboardTotalsView dashboardTotals() { return repository.dashboardTotals(); }
    public List<PurchaseOrder> findByUserId(Long userId) { return repository.findByUserIdOrderByCreatedAtDescIdDesc(userId); }
    public Optional<PurchaseOrder> findByUserIdAndCheckoutKey(Long userId, UUID key) { return repository.findByUserIdAndCheckoutKey(userId, key); }
    public Optional<PurchaseOrder> findByIdAndUserId(Long id, Long userId) { return repository.findByIdAndUserId(id, userId); }
    public PurchaseOrder save(PurchaseOrder order) { return repository.saveAndFlush(order); }
    public Optional<PurchaseOrder> findById(Long id) { return repository.findById(id); }
    public Optional<PurchaseOrder> findByIdForUpdate(Long id) { return repository.findByIdForUpdate(id); }
    public org.springframework.data.domain.Page<PurchaseOrder> findAdminOrders(String query, se327.bookstore.entity.OrderStatus status, org.springframework.data.domain.Pageable pageable) {
        org.springframework.data.jpa.domain.Specification<PurchaseOrder> filters = (root, cq, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (!query.isBlank()) {
                String pattern = "%" + query.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                var matches = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
                matches.add(cb.like(cb.lower(root.get("user").get("username")), pattern, '\\'));
                matches.add(cb.like(cb.lower(root.get("user").get("email")), pattern, '\\'));
                try { matches.add(cb.equal(root.get("id"), Long.parseLong(query))); } catch (NumberFormatException ignored) { }
                predicates.add(cb.or(matches.toArray(jakarta.persistence.criteria.Predicate[]::new)));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return repository.findAll(filters, pageable);
    }
}
