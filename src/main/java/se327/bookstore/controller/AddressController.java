package se327.bookstore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.AddressDto;
import se327.bookstore.dto.AddressRequest;
import se327.bookstore.service.AddressService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {
    private final AddressService addressService;

    @GetMapping
    public List<AddressDto> getAddresses(Principal principal) {
        return addressService.getAddresses(principal.getName());
    }

    @PostMapping
    public ResponseEntity<AddressDto> addAddress(Principal principal, @Valid @RequestBody AddressRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addressService.addAddress(principal.getName(), request));
    }

    @PutMapping("/{id}")
    public AddressDto updateAddress(Principal principal, @PathVariable @Positive Long id,
                                    @Valid @RequestBody AddressRequest request) {
        return addressService.updateAddress(principal.getName(), id, request);
    }

    @PutMapping("/{id}/default")
    public AddressDto setDefault(Principal principal, @PathVariable @Positive Long id) {
        return addressService.setDefault(principal.getName(), id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(Principal principal, @PathVariable @Positive Long id) {
        addressService.deleteAddress(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
