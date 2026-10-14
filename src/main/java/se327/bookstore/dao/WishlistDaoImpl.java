package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.WishlistItem;
import se327.bookstore.repository.WishlistItemRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class WishlistDaoImpl implements WishlistDao {

    private final WishlistItemRepository wishlistItemRepository;

    @Override
    public WishlistItem save(WishlistItem item) {
        return wishlistItemRepository.save(item);
    }

    @Override
    public List<WishlistItem> findByUserId(Long userId) {
        return wishlistItemRepository.findByUserId(userId);
    }

    @Override
    public Optional<WishlistItem> findByUserIdAndProductId(Long userId, Long productId) {
        return wishlistItemRepository.findByUserIdAndProductId(userId, productId);
    }

    @Override
    public boolean existsByUserIdAndProductId(Long userId, Long productId) {
        return wishlistItemRepository.existsByUserIdAndProductId(userId, productId);
    }

    @Override
    public void delete(WishlistItem item) {
        wishlistItemRepository.delete(item);
    }

    @Override
    public void deleteByUserIdAndProductId(Long userId, Long productId) {
        wishlistItemRepository.deleteByUserIdAndProductId(userId, productId);
    }
}
