package se327.bookstore.dto;

import se327.bookstore.entity.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryDto(Long id, OrderStatus status, String paymentDisplay, BigDecimal total, Instant createdAt) { }
