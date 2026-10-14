package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.AuthResponseDto;
import se327.bookstore.dto.ChangePasswordRequest;
import se327.bookstore.dto.UpdateProfileRequest;
import se327.bookstore.dto.UserDto;
import se327.bookstore.entity.User;
import se327.bookstore.exception.DuplicateEmailException;
import se327.bookstore.exception.InvalidCurrentPasswordException;
import se327.bookstore.exception.UserNotFoundException;
import se327.bookstore.security.JwtTokenService;

@Service
@RequiredArgsConstructor
public class CustomerProfileServiceImpl implements CustomerProfileService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Override
    @Transactional(readOnly = true)
    public UserDto getProfile(String email) {
        if (email == null || email.isBlank()) {
            throw new UserNotFoundException("User not found.");
        }
        User user = userDao.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return UserDto.fromEntity(user);
    }

    @Override
    @Transactional
    public AuthResponseDto updateProfile(String currentEmail, UpdateProfileRequest request) {
        User user = userDao.findByEmail(currentEmail.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + currentEmail));

        String newEmail = request.email().trim().toLowerCase();

        if (!user.getEmail().equalsIgnoreCase(newEmail)) {
            if (userDao.existsByEmailAndIdNot(newEmail, user.getId())) {
                throw new DuplicateEmailException("An account with this email already exists.");
            }
            user.setEmail(newEmail);
        }

        user.setUsername(request.username().trim());
        user.setPhone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null);
        user.setAvatarUrl(request.avatarUrl() != null && !request.avatarUrl().isBlank() ? request.avatarUrl().trim() : null);

        User savedUser = userDao.save(user);
        String token = jwtTokenService.generateToken(savedUser);
        return new AuthResponseDto(token, UserDto.fromEntity(savedUser));
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userDao.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException("Incorrect current password.");
        }

        if (request.newPassword().equals(request.currentPassword())) {
            throw new InvalidCurrentPasswordException("New password cannot be the same as the current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userDao.save(user);
    }
}
