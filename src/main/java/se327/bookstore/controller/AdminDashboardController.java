package se327.bookstore.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.AdminDashboardDto;
import se327.bookstore.service.AdminDashboardService;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {
    private final AdminDashboardService service;

    @GetMapping
    public AdminDashboardDto get(@RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page) {
        return service.get(page);
    }
}
