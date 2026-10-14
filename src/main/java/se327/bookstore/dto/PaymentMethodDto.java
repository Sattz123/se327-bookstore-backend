package se327.bookstore.dto;

import java.time.Instant;

public record PaymentMethodDto(Long id, String provider, String cardholderName, String cardNumberMasked,
                               int expiryMonth, int expiryYear, boolean isDefault, Instant createdAt) {
}
