package se327.bookstore.dto;
import jakarta.validation.constraints.*;
import se327.bookstore.entity.OrderStatus;
public record AdminOrderStatusRequest(@NotNull OrderStatus status, @NotNull @PositiveOrZero Long version) { }
