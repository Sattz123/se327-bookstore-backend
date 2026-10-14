package se327.bookstore.dao;

import se327.bookstore.entity.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface ProductDao {
    Page<Product> findVisibleProducts(String query, Long categoryId, Pageable pageable);

    Optional<Product> findVisibleProductById(Long id);

    Optional<Product> findByIdForUpdate(Long id);
}
