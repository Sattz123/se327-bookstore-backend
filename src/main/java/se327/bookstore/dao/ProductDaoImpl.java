package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.Product;
import se327.bookstore.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductDaoImpl implements ProductDao {

    private final ProductRepository productRepository;

    @Override
    public List<Product> findVisibleProducts() {
        return productRepository.findByVisibleTrueAndCategoryVisibleTrueOrderByIdAsc();
    }

    @Override
    public Optional<Product> findVisibleProductById(Long id) {
        return productRepository.findByIdAndVisibleTrueAndCategoryVisibleTrue(id);
    }
}
