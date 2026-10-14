package se327.bookstore.dto;

public record VerifyResetTokenResponse(
        boolean valid,
        String message
) {
}
