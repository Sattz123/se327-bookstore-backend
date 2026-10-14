package se327.bookstore.dto;

import jakarta.validation.constraints.*;

public record AdminInventoryRequest(@NotNull @PositiveOrZero Integer stockQuantity, @NotNull @PositiveOrZero Long version) { }
