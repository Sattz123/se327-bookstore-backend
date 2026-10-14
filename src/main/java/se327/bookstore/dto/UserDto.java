package se327.bookstore.dto;

import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;

import java.time.Instant;

public record UserDto(
        Long id,
        String username,
        String email,
        String phone,
        String avatarUrl,
        Role role,
        Instant createdAt
) {
    public static UserDto fromEntity(User user) {
        return new UserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
