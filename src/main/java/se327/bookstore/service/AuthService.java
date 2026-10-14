package se327.bookstore.service;

import se327.bookstore.dto.AuthResponseDto;
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.dto.RegisterRequest;
import se327.bookstore.dto.UserDto;

public interface AuthService {
    AuthResponseDto register(RegisterRequest request);

    AuthResponseDto login(LoginRequest request);

    UserDto getCurrentUser(String email);
}
