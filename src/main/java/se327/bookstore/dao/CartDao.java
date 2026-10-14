package se327.bookstore.dao;

import se327.bookstore.entity.CartItem;
import java.util.List;
import java.util.Optional;

public interface CartDao {
    List<CartItem> findByUserId(Long userId);
    Optional<CartItem> findByIdAndUserId(Long id, Long userId);
    void save(CartItem item);
    void delete(CartItem item);
}
