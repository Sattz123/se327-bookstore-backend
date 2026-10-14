package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import se327.bookstore.entity.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = """
            SELECT u.id, u.username, u.email, u.phone, u.created_at AS "createdAt",
                   count(o.id) AS "orderCount", coalesce(sum(o.total), 0) AS "totalSpent"
            FROM users u LEFT JOIN orders o ON o.user_id = u.id AND o.status <> 'CANCELLED'
            WHERE u.role = 'CUSTOMER' AND (strpos(lower(u.username), lower(:q)) > 0 OR strpos(lower(u.email), lower(:q)) > 0)
            GROUP BY u.id ORDER BY u.created_at DESC, u.id DESC
            """, countQuery = """
            SELECT count(*) FROM users u WHERE u.role = 'CUSTOMER'
            AND (strpos(lower(u.username), lower(:q)) > 0 OR strpos(lower(u.email), lower(:q)) > 0)
            """, nativeQuery = true)
    org.springframework.data.domain.Page<CustomerSummaryView> findCustomerSummaries(@Param("q") String query, org.springframework.data.domain.Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
