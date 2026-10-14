package se327.bookstore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.*;
import se327.bookstore.entity.OrderStatus;
import se327.bookstore.service.AdminOrderService;

@RestController @RequestMapping("/api/admin/orders") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {
    private final AdminOrderService service;
    @GetMapping public AdminOrderPageDto list(@RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) OrderStatus status, @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page) { return service.list(q, status, page); }
    @GetMapping("/{id}") public AdminOrderDetailDto get(@PathVariable @Positive Long id) { return service.get(id); }
    @PatchMapping("/{id}/status") public AdminOrderDetailDto status(@PathVariable @Positive Long id, @Valid @RequestBody AdminOrderStatusRequest request) { return service.updateStatus(id, request); }
}
