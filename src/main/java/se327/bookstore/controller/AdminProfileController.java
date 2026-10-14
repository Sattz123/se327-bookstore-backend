package se327.bookstore.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.*;
import se327.bookstore.service.CustomerProfileService;

import java.security.Principal;

@RestController
@RequestMapping("/api/admin/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProfileController {
    private final CustomerProfileService profiles;

    @GetMapping
    public UserDto get(Principal principal) {
        return profiles.getProfile(principal.getName());
    }

    @PutMapping
    public AuthResponseDto update(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.updateProfile(principal.getName(), request);
    }

    @PutMapping("/password")
    public MessageResponseDto password(Principal principal, @Valid @RequestBody ChangePasswordRequest request) {
        profiles.changePassword(principal.getName(), request);
        return new MessageResponseDto("Password changed successfully.");
    }
}
