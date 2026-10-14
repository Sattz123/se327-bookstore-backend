package se327.bookstore.dto;

import java.util.List;

public record AdminCustomerPageDto(List<AdminCustomerDto> content, int page, int size, long totalElements, int totalPages) { }
