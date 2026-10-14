package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se327.bookstore.entity.PurchaseOrder;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

public interface OrderRepository extends JpaRepository<PurchaseOrder, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<PurchaseOrder> {
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT (SELECT count(*) FROM products) AS totalProducts,
                   (SELECT count(*) FROM orders) AS totalOrders,
                   (SELECT count(*) FROM users WHERE role = 'CUSTOMER') AS totalCustomers
            """, nativeQuery = true)
    DashboardTotalsView dashboardTotals();
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "user")
    org.springframework.data.domain.Page<PurchaseOrder> findAll(org.springframework.data.jpa.domain.Specification<PurchaseOrder> filters, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<PurchaseOrder> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    Optional<PurchaseOrder> findByUserIdAndCheckoutKey(Long userId, UUID checkoutKey);
    Optional<PurchaseOrder> findByIdAndUserId(Long id, Long userId);
}
