package se327.bookstore.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import se327.bookstore.entity.BookFormat;

public record AddToCartRequest(@NotNull @Positive Long productId, @NotNull BookFormat format,
                               @NotNull @Positive Integer quantity) { }
