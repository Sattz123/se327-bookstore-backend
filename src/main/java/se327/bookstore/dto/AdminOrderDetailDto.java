package se327.bookstore.dto;

import se327.bookstore.entity.OrderStatus;
import java.time.Instant;
import java.util.List;

public record AdminOrderDetailDto(OrderDto order, Long customerId, String customerName, String customerEmail,
                                   String customerPhone, long version, Instant updatedAt, List<OrderStatus> allowedStatuses) { }
