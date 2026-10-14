package se327.bookstore.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se327.bookstore.dto.CategoryDto;
import se327.bookstore.service.CategoryService;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService categoryService;

    @GetMapping
    public List<CategoryDto> getCategories() {
        return categoryService.getCategories();
    }

    @GetMapping("/{id}")
    public CategoryDto getCategory(@PathVariable @Positive Long id) {
        return categoryService.getCategory(id);
    }
}
