package se327.bookstore.dto;

import jakarta.validation.constraints.*;
import se327.bookstore.entity.PaymentType;
import java.util.UUID;

public record CheckoutRequest(@NotNull @Positive Long addressId, @NotNull PaymentType paymentType,
                              @Positive Long paymentMethodId, @NotNull UUID checkoutKey,
                              @NotNull @Pattern(regexp = "[a-f0-9]{64}") String cartFingerprint) { }
