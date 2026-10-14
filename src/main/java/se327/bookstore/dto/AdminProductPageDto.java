package se327.bookstore.dto;

import java.util.List;

public record AdminProductPageDto(List<AdminProductDto> content, int page, int size, long totalElements, int totalPages) { }
