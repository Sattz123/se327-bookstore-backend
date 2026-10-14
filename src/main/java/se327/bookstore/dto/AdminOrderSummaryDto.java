package se327.bookstore.dto;

import se327.bookstore.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record AdminOrderSummaryDto(Long id, Long customerId, String customerName, String customerEmail, OrderStatus status,
                                    String paymentDisplay, BigDecimal total, Instant createdAt) { }
