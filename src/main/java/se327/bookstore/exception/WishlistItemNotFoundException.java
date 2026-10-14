package se327.bookstore.exception;

public class WishlistItemNotFoundException extends RuntimeException {

    public WishlistItemNotFoundException(String message) {
        super(message);
    }
}
