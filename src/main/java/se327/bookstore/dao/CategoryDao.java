package se327.bookstore.dao;

import se327.bookstore.entity.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryDao {
    List<Category> findVisibleCategories();

    Optional<Category> findVisibleCategoryById(Long id);

    Optional<Category> findById(Long id);
    List<Category> findAll();
    Optional<Category> findByIdForUpdate(Long id);
    Category save(Category category);
    void delete(Category category);
}
