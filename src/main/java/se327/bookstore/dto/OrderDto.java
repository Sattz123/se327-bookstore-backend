package se327.bookstore.dto;

import se327.bookstore.entity.OrderStatus;
import se327.bookstore.entity.PaymentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(Long id, OrderStatus status, PaymentType paymentType, String paymentDisplay,
                       String recipientName, String shippingPhone, String shippingAddress,
                       BigDecimal total, Instant createdAt, List<OrderItemDto> items) { }
