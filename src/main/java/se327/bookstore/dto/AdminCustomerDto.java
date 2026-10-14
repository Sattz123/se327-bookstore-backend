package se327.bookstore.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record AdminCustomerDto(Long id, String username, String email, String phone, Instant createdAt,
                                long orderCount, BigDecimal totalSpent) { }
