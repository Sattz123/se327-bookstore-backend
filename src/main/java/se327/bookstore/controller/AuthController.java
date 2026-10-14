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
import se327.bookstore.dto.AuthResponseDto;
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.dto.MessageResponseDto;
import se327.bookstore.dto.RegisterRequest;
import se327.bookstore.dto.UserDto;
import se327.bookstore.service.AuthService;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

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
}
