package se327.bookstore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.*;
import se327.bookstore.service.CheckoutService;
import java.security.Principal;

@RestController @RequestMapping("/api/checkout") @RequiredArgsConstructor
public class CheckoutController {
    private final CheckoutService service;
    @GetMapping("/review")
    public CheckoutReviewDto review(Principal principal) { return service.review(principal.getName()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public OrderDto checkout(Principal principal, @Valid @RequestBody CheckoutRequest request) {
        return service.checkout(principal.getName(), request);
    }
    @GetMapping("/orders/{id}")
    public OrderDto confirmation(Principal principal, @PathVariable @Positive Long id) {
        return service.confirmation(principal.getName(), id);
    }
}
