package se327.bookstore.dao;

import se327.bookstore.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductDao {
    List<Product> findVisibleProducts();

    Optional<Product> findVisibleProductById(Long id);
}
