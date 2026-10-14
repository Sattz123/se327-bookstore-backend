package se327.bookstore.dto;

import se327.bookstore.entity.OrderStatus;
import se327.bookstore.entity.PaymentType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(Long id, OrderStatus status, PaymentType paymentType, String paymentDisplay,
                       String recipientName, String shippingPhone, String shippingAddress,
                       BigDecimal total, Instant createdAt, List<OrderItemDto> items) {
    public static OrderDto fromEntity(se327.bookstore.entity.PurchaseOrder order) {
        return new OrderDto(order.getId(), order.getStatus(), order.getPaymentType(), order.getPaymentDisplay(), order.getRecipientName(),
                order.getShippingPhone(), order.getShippingAddress(), order.getTotal(), order.getCreatedAt(), order.getItems().stream()
                .map(i -> new OrderItemDto(i.getProductId(), i.getTitle(), i.getFormat(), i.getQuantity(), i.getUnitPrice(), i.getLineTotal())).toList());
    }
}
