package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.CartItem;
import se327.bookstore.repository.CartItemRepository;
import java.util.List;
import java.util.Optional;

@Repository @RequiredArgsConstructor
public class CartDaoImpl implements CartDao {
    private final CartItemRepository repository;
    public List<CartItem> findByUserId(Long userId) { return repository.findByUserIdOrderByIdAsc(userId); }
    public Optional<CartItem> findByIdAndUserId(Long id, Long userId) { return repository.findByIdAndUserId(id, userId); }
    public void save(CartItem item) { repository.saveAndFlush(item); }
    public void delete(CartItem item) { repository.delete(item); repository.flush(); }
}
