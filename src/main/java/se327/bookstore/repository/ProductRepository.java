package se327.bookstore.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import se327.bookstore.entity.Product;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    long countByCategoryId(Long categoryId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> specification, Pageable pageable);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndVisibleTrueAndCategoryVisibleTrue(Long id);
}
