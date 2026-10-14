package se327.bookstore.service;

import se327.bookstore.dto.*;

public interface CartService {
    CartDto getCart(String email);
    CartDto addItem(String email, AddToCartRequest request);
    CartDto updateQuantity(String email, Long id, UpdateCartQuantityRequest request);
    CartDto removeItem(String email, Long id);
}
