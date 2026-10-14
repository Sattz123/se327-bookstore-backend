package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.CategoryDao;
import se327.bookstore.dto.CategoryDto;
import se327.bookstore.entity.Category;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryServiceImpl implements CategoryService {
    private final CategoryDao categoryDao;

    @Override
    public List<CategoryDto> getCategories() {
        return categoryDao.findVisibleCategories().stream().map(this::toDto).toList();
    }

    @Override
    public CategoryDto getCategory(Long id) {
        return categoryDao.findVisibleCategoryById(id).map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found."));
    }

    private CategoryDto toDto(Category category) {
        return new CategoryDto(category.getId(), category.getName(), category.getImageUrl());
    }
}
