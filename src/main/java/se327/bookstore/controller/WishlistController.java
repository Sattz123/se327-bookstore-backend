package se327.bookstore.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se327.bookstore.dto.AddToWishlistRequest;
import se327.bookstore.dto.WishlistItemDto;
import se327.bookstore.service.WishlistService;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/wishlist")
@RequiredArgsConstructor
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<List<WishlistItemDto>> getWishlist(Principal principal) {
        List<WishlistItemDto> items = wishlistService.getWishlistForUser(principal.getName());
        return ResponseEntity.ok(items);
    }

    @PostMapping
    public ResponseEntity<WishlistItemDto> addToWishlist(
            Principal principal,
            @Valid @RequestBody AddToWishlistRequest request) {
        WishlistItemDto item = wishlistService.addToWishlist(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(item);
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> removeFromWishlist(
            Principal principal,
            @PathVariable Long productId) {
        wishlistService.removeFromWishlist(principal.getName(), productId);
        return ResponseEntity.noContent().build();
    }
}
