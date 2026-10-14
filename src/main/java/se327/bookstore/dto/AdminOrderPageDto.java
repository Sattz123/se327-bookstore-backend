package se327.bookstore.dto;
import java.util.List;
public record AdminOrderPageDto(List<AdminOrderSummaryDto> content, int page, int size, long totalElements, int totalPages) { }
