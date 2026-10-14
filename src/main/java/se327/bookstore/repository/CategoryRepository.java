package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se327.bookstore.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByVisibleTrueOrderByIdAsc();

    Optional<Category> findByIdAndVisibleTrue(Long id);
}
