package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.AuthResponseDto;
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.dto.RegisterRequest;
import se327.bookstore.dto.UserDto;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.exception.DuplicateEmailException;
import se327.bookstore.exception.UserNotFoundException;
import se327.bookstore.security.JwtTokenService;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthResponseDto register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userDao.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("An account with this email already exists.");
        }

        User user = User.builder()
                .username(request.username().trim())
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .phone(request.phone() != null && !request.phone().isBlank() ? request.phone().trim() : null)
                .role(Role.CUSTOMER)
                .build();

        User savedUser = userDao.save(user);
        String token = jwtTokenService.generateToken(savedUser);
        return new AuthResponseDto(token, UserDto.fromEntity(savedUser));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponseDto login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
            );
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password.");
        }

        User user = userDao.findByEmail(normalizedEmail)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + normalizedEmail));

        String token = jwtTokenService.generateToken(user);
        return new AuthResponseDto(token, UserDto.fromEntity(user));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String email) {
        if (email == null || email.isBlank()) {
            throw new UserNotFoundException("User not found.");
        }
        User user = userDao.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
        return UserDto.fromEntity(user);
    }
}
