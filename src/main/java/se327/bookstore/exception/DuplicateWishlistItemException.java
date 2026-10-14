package se327.bookstore.exception;

public class DuplicateWishlistItemException extends RuntimeException {

    public DuplicateWishlistItemException(String message) {
        super(message);
    }
}
