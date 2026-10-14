package se327.bookstore.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se327.bookstore.dto.UserDto;
import se327.bookstore.service.AuthService;
import java.security.Principal;

@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminSessionController {
    private final AuthService authService;

    @GetMapping("/session")
    public UserDto session(Principal principal) { return authService.getCurrentUser(principal.getName()); }
}
