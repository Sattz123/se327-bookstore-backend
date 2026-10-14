package se327.bookstore.dto;

import se327.bookstore.entity.BookFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AdminProductDto(Long id, String name, String description, String author, BigDecimal price,
                               int stockQuantity, boolean visible, AdminCategoryDto category,
                               List<BookFormat> formats, List<String> images, long version, Instant updatedAt) { }
