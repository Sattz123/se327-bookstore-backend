package se327.bookstore.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import se327.bookstore.dto.AuthResponseDto;
import se327.bookstore.dto.ForgotPasswordRequest;
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.dto.MessageResponseDto;
import se327.bookstore.dto.RegisterRequest;
import se327.bookstore.dto.ResetPasswordRequest;
import se327.bookstore.dto.UserDto;
import se327.bookstore.dto.VerifyResetTokenResponse;
import se327.bookstore.service.AuthService;
import se327.bookstore.service.PasswordRecoveryService;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordRecoveryService passwordRecoveryService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDto> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponseDto response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequest request) {
        AuthResponseDto response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> getCurrentUser(Principal principal) {
        UserDto user = authService.getCurrentUser(principal.getName());
        return ResponseEntity.ok(user);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponseDto> logout() {
        return ResponseEntity.ok(new MessageResponseDto("Logged out successfully."));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponseDto> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        MessageResponseDto response = passwordRecoveryService.requestPasswordReset(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/verify-reset-token")
    public ResponseEntity<VerifyResetTokenResponse> verifyResetToken(
            @RequestParam(required = false) String token) {
        VerifyResetTokenResponse response = passwordRecoveryService.verifyResetToken(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponseDto> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        MessageResponseDto response = passwordRecoveryService.resetPassword(request);
        return ResponseEntity.ok(response);
    }
}
