package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se327.bookstore.entity.CartItem;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUserIdOrderByIdAsc(Long userId);
    Optional<CartItem> findByIdAndUserId(Long id, Long userId);
}
