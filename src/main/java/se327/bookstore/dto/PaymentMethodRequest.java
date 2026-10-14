package se327.bookstore.dto;

import jakarta.validation.constraints.*;

public record PaymentMethodRequest(
        @NotBlank @Size(max = 50) String provider,
        @NotBlank @Size(max = 100) String cardholderName,
        @NotNull @Pattern(regexp = "\\d{4}", message = "Enter exactly four digits.") String lastFour,
        @Min(1) @Max(12) int expiryMonth,
        @Min(2000) @Max(9999) int expiryYear,
        boolean isDefault) {
}
