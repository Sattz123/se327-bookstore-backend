package se327.bookstore.service;

import se327.bookstore.dto.CategoryDto;

import java.util.List;

public interface CategoryService {
    List<CategoryDto> getCategories();

    CategoryDto getCategory(Long id);
}
