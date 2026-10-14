package se327.bookstore.dto;

import se327.bookstore.entity.BookFormat;
import java.math.BigDecimal;

public record OrderItemDto(Long productId, String title, BookFormat format, int quantity,
                           BigDecimal unitPrice, BigDecimal lineTotal) { }
