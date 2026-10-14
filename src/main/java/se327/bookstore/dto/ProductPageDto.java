package se327.bookstore.dto;

import java.util.List;

public record ProductPageDto(List<ProductDto> content, int page, int size,
                             long totalElements, int totalPages) {
}
