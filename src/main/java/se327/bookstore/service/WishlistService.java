package se327.bookstore.service;

import se327.bookstore.dto.AddToWishlistRequest;
import se327.bookstore.dto.WishlistItemDto;

import java.util.List;

public interface WishlistService {

    List<WishlistItemDto> getWishlistForUser(String userEmail);

    WishlistItemDto addToWishlist(String userEmail, AddToWishlistRequest request);

    void removeFromWishlist(String userEmail, Long productId);
}
