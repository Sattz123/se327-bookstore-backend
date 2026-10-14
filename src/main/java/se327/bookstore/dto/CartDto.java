package se327.bookstore.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartDto(List<CartItemDto> items, long totalQuantity, BigDecimal total) { }
