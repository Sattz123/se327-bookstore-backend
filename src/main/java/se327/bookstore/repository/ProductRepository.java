package se327.bookstore.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import se327.bookstore.entity.Product;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "category")
    List<Product> findByVisibleTrueAndCategoryVisibleTrueOrderByIdAsc();

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndVisibleTrueAndCategoryVisibleTrue(Long id);
}
