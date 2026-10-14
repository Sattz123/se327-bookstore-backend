package se327.bookstore.dto;

public record AuthResponseDto(
        String token,
        UserDto user
) {
}
