package se327.bookstore.controller;

import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.AdminCustomerPageDto;
import se327.bookstore.service.AdminCustomerService;

@RestController @RequestMapping("/api/admin/customers") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminCustomerController {
    private final AdminCustomerService service;
    @GetMapping
    public AdminCustomerPageDto list(@RequestParam(defaultValue = "") @Size(max = 100) String q,
                                     @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page) {
        return service.list(q, page);
    }
}
