package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.Category;
import se327.bookstore.repository.CategoryRepository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class CategoryDaoImpl implements CategoryDao {
    private final CategoryRepository categoryRepository;

    @Override
    public List<Category> findVisibleCategories() {
        return categoryRepository.findByVisibleTrueOrderByIdAsc();
    }

    @Override
    public Optional<Category> findVisibleCategoryById(Long id) {
        return categoryRepository.findByIdAndVisibleTrue(id);
    }

    @Override public Optional<Category> findById(Long id) { return categoryRepository.findById(id); }
    @Override public List<Category> findAll() { return categoryRepository.findAll(org.springframework.data.domain.Sort.by("name", "id")); }
    @Override public Optional<Category> findByIdForUpdate(Long id) { return categoryRepository.findByIdForUpdate(id); }
    @Override public Category save(Category category) { return categoryRepository.saveAndFlush(category); }
    @Override public void delete(Category category) { categoryRepository.delete(category); categoryRepository.flush(); }
}
