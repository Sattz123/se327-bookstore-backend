package se327.bookstore.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank(message = "Label is required.") @Size(max = 50) String label,
        @NotBlank(message = "Recipient name is required.") @Size(max = 200) String fullName,
        @Size(max = 20) String phone,
        @NotBlank(message = "Address line is required.") @Size(max = 500) String addressLine,
        @Size(max = 100) String city,
        @Size(max = 100) String province,
        @Size(max = 10) String postalCode,
        boolean isDefault
) {
}
