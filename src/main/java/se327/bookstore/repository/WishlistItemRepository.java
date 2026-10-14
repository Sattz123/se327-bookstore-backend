package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se327.bookstore.entity.WishlistItem;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @Query("SELECT w FROM WishlistItem w JOIN FETCH w.product p JOIN FETCH p.category WHERE w.user.id = :userId ORDER BY w.createdAt DESC")
    List<WishlistItem> findByUserId(@Param("userId") Long userId);

    Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    @Modifying
    @Query("DELETE FROM WishlistItem w WHERE w.user.id = :userId AND w.product.id = :productId")
    void deleteByUserIdAndProductId(@Param("userId") Long userId, @Param("productId") Long productId);
}
