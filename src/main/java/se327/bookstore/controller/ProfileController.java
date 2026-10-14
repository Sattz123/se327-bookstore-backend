package se327.bookstore.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se327.bookstore.dto.AuthResponseDto;
import se327.bookstore.dto.ChangePasswordRequest;
import se327.bookstore.dto.MessageResponseDto;
import se327.bookstore.dto.UpdateProfileRequest;
import se327.bookstore.dto.UserDto;
import se327.bookstore.service.CustomerProfileService;

import java.security.Principal;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CustomerProfileService profileService;

    @GetMapping
    public ResponseEntity<UserDto> getProfile(Principal principal) {
        return ResponseEntity.ok(profileService.getProfile(principal.getName()));
    }

    @PutMapping
    public ResponseEntity<AuthResponseDto> updateProfile(
            Principal principal,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(profileService.updateProfile(principal.getName(), request));
    }

    @PutMapping("/password")
    public ResponseEntity<MessageResponseDto> changePassword(
            Principal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        profileService.changePassword(principal.getName(), request);
        return ResponseEntity.ok(new MessageResponseDto("Password changed successfully."));
    }
}
