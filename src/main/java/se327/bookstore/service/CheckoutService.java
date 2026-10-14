package se327.bookstore.service;

import se327.bookstore.dto.*;

public interface CheckoutService {
    CheckoutReviewDto review(String email);
    OrderDto checkout(String email, CheckoutRequest request);
    OrderDto confirmation(String email, Long orderId);
}
