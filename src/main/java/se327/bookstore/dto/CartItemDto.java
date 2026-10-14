package se327.bookstore.dto;

import se327.bookstore.entity.BookFormat;
import java.math.BigDecimal;

public record CartItemDto(Long id, Long productId, String name, String image, BookFormat format,
                          int quantity, BigDecimal unitPrice, BigDecimal lineTotal,
                          int stockQuantity, boolean available) { }
