package se327.bookstore.dto;

import se327.bookstore.entity.BookFormat;

import java.math.BigDecimal;
import java.util.List;

public record ProductDto(Long id, String name, String description, BigDecimal price,
                         String author, int stockQuantity, CategoryDto category,
                         List<String> images, List<BookFormat> formats) {
}
