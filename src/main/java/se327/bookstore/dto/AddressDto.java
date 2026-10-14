package se327.bookstore.dto;

import java.time.Instant;

public record AddressDto(Long id, String label, String fullName, String phone,
                         String addressLine, String city, String province, String postalCode,
                         boolean isDefault, Instant createdAt, Instant updatedAt) {
}
