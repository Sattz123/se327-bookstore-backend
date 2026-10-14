package se327.bookstore.dto;

import jakarta.validation.constraints.*;

public record AdminVisibilityRequest(@NotNull Boolean visible, @NotNull @PositiveOrZero Long version) { }
