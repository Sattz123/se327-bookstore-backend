package se327.bookstore.dto;

import jakarta.validation.constraints.*;

public record AdminCategoryRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String imageUrl,
                                    @NotNull Boolean visible, @PositiveOrZero Long version) { }
