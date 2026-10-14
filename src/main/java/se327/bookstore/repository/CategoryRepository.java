package se327.bookstore.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import se327.bookstore.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<Category> findByVisibleTrueOrderByIdAsc();

    Optional<Category> findByIdAndVisibleTrue(Long id);
}
