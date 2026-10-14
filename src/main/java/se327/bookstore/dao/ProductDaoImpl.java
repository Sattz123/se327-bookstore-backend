package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.Product;
import se327.bookstore.repository.ProductRepository;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductDaoImpl implements ProductDao {

    private final ProductRepository productRepository;

    @Override
    public Page<Product> findVisibleProducts(String query, Long categoryId, Pageable pageable) {
        Specification<Product> filters = (root, criteriaQuery, cb) -> {
            var predicates = new ArrayList<Predicate>();
            predicates.add(cb.isTrue(root.get("visible")));
            predicates.add(cb.isTrue(root.get("category").get("visible")));
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (!query.isBlank()) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("author")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return productRepository.findAll(filters, pageable);
    }

    @Override
    public Optional<Product> findVisibleProductById(Long id) {
        return productRepository.findByIdAndVisibleTrueAndCategoryVisibleTrue(id);
    }

    @Override
    public Optional<Product> findByIdForUpdate(Long id) {
        return productRepository.findByIdForUpdate(id);
    }

    @Override
    public Page<Product> findAdminProducts(String query, Long categoryId, Boolean visible, Pageable pageable) {
        Specification<Product> filters = (root, criteriaQuery, cb) -> {
            var predicates = new ArrayList<Predicate>();
            if (visible != null) predicates.add(cb.equal(root.get("visible"), visible));
            if (categoryId != null) predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            if (!query.isBlank()) {
                String pattern = "%" + query.toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                predicates.add(cb.or(cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("author")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return productRepository.findAll(filters, pageable);
    }
    @Override public Optional<Product> findById(Long id) { return productRepository.findById(id); }
    @Override public Product save(Product product) { return productRepository.saveAndFlush(product); }
    @Override public long countByCategoryId(Long categoryId) { return productRepository.countByCategoryId(categoryId); }
}
