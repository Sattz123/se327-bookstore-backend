package se327.bookstore.dto;

import java.util.List;

public record AdminDashboardDto(long totalProducts, long totalOrders, long totalCustomers,
                                List<AdminOrderSummaryDto> recentOrders, int page, int size, int totalPages) { }
