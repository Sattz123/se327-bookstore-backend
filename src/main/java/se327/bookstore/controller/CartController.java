package se327.bookstore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.*;
import se327.bookstore.service.CartService;
import java.security.Principal;

@RestController @RequestMapping("/api/cart") @RequiredArgsConstructor
public class CartController {
    private final CartService service;
    @GetMapping
    public CartDto getCart(Principal principal) { return service.getCart(principal.getName()); }
    @PostMapping("/items") @ResponseStatus(HttpStatus.CREATED)
    public CartDto addItem(Principal principal, @Valid @RequestBody AddToCartRequest request) {
        return service.addItem(principal.getName(), request);
    }
    @PutMapping("/items/{id}")
    public CartDto updateQuantity(Principal principal, @PathVariable @Positive Long id, @Valid @RequestBody UpdateCartQuantityRequest request) {
        return service.updateQuantity(principal.getName(), id, request);
    }
    @DeleteMapping("/items/{id}")
    public CartDto removeItem(Principal principal, @PathVariable @Positive Long id) {
        return service.removeItem(principal.getName(), id);
    }
}
