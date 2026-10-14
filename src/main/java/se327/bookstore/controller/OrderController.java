package se327.bookstore.controller;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.OrderDto;
import se327.bookstore.dto.OrderSummaryDto;
import se327.bookstore.service.OrderService;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api/orders") @RequiredArgsConstructor
public class OrderController {
    private final OrderService service;
    @GetMapping
    public List<OrderSummaryDto> getOrders(Principal principal) { return service.getOrders(principal.getName()); }
    @GetMapping("/{id}")
    public OrderDto getOrder(Principal principal, @PathVariable @Positive Long id) {
        return service.getOrder(principal.getName(), id);
    }
}
