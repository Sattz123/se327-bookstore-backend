package se327.bookstore.dto;

import jakarta.validation.constraints.*;
import se327.bookstore.entity.BookFormat;
import java.math.BigDecimal;
import java.util.Set;

public record AdminProductRequest(@NotBlank @Size(max = 255) String name, @Size(max = 10000) String description,
                                   @Size(max = 255) String author, @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal price,
                                   @NotNull @PositiveOrZero Integer stockQuantity, @NotNull @Positive Long categoryId,
                                   @NotEmpty Set<@NotNull BookFormat> formats, @NotNull Boolean visible,
                                   @PositiveOrZero Long version) { }
