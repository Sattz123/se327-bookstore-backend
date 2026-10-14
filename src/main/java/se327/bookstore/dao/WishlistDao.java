package se327.bookstore.dao;

import se327.bookstore.entity.WishlistItem;

import java.util.List;
import java.util.Optional;

public interface WishlistDao {

    WishlistItem save(WishlistItem item);

    List<WishlistItem> findByUserId(Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    void delete(WishlistItem item);

    void deleteByUserIdAndProductId(Long userId, Long productId);
}
