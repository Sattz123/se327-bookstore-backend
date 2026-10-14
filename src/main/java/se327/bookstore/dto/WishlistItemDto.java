package se327.bookstore.dto;

import java.time.Instant;

public record WishlistItemDto(
        Long id,
        ProductDto product,
        Instant createdAt
) {
}
