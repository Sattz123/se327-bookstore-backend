package se327.bookstore.service;

import se327.bookstore.dto.AuthResponseDto;
import se327.bookstore.dto.ChangePasswordRequest;
import se327.bookstore.dto.UpdateProfileRequest;
import se327.bookstore.dto.UserDto;

public interface CustomerProfileService {

    UserDto getProfile(String email);

    AuthResponseDto updateProfile(String currentEmail, UpdateProfileRequest request);

    void changePassword(String email, ChangePasswordRequest request);
}
