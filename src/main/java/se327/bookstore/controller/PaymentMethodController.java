package se327.bookstore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.PaymentMethodDto;
import se327.bookstore.dto.PaymentMethodRequest;
import se327.bookstore.service.PaymentMethodService;
import java.security.Principal;
import java.util.List;

@RestController @RequestMapping("/api/payment-methods") @RequiredArgsConstructor
public class PaymentMethodController {
    private final PaymentMethodService service;

    @GetMapping
    public List<PaymentMethodDto> getPaymentMethods(Principal principal) {
        return service.getPaymentMethods(principal.getName());
    }
    @PostMapping
    public ResponseEntity<PaymentMethodDto> addPaymentMethod(Principal principal, @Valid @RequestBody PaymentMethodRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addPaymentMethod(principal.getName(), request));
    }
    @PutMapping("/{id}/default")
    public PaymentMethodDto setDefault(Principal principal, @PathVariable @Positive Long id) {
        return service.setDefault(principal.getName(), id);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePaymentMethod(Principal principal, @PathVariable @Positive Long id) {
        service.deletePaymentMethod(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
