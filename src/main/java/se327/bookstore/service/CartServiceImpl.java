package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.*;
import se327.bookstore.dto.*;
import se327.bookstore.entity.*;
import se327.bookstore.exception.BookNotFoundException;
import se327.bookstore.exception.UserNotFoundException;
import java.math.BigDecimal;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class CartServiceImpl implements CartService {
    private final CartDao cartDao;
    private final UserDao userDao;
    private final ProductDao productDao;

    @Override @Transactional(readOnly = true)
    public CartDto getCart(String email) {
        User user = userDao.findByEmail(email).orElseThrow(() -> new UserNotFoundException("User not found."));
        return cart(user.getId());
    }

    @Override
    public CartDto addItem(String email, AddToCartRequest request) {
        User user = lockUser(email);
        Product product = productDao.findVisibleProductById(request.productId())
                .orElseThrow(() -> new BookNotFoundException("Book not found."));
        validateFormat(product, request.format());
        List<CartItem> items = cartDao.findByUserId(user.getId());
        CartItem item = items.stream().filter(i -> i.getProduct().getId().equals(product.getId())
                && i.getFormat() == request.format()).findFirst().orElseGet(() ->
                CartItem.builder().user(user).product(product).format(request.format()).build());
        long combined = (long) item.getQuantity() + request.quantity();
        long productQuantity = quantityForProduct(items, product.getId()) + request.quantity();
        validateStock(product, productQuantity);
        item.setQuantity(Math.toIntExact(combined));
        cartDao.save(item);
        return cart(user.getId());
    }

    @Override
    public CartDto updateQuantity(String email, Long id, UpdateCartQuantityRequest request) {
        User user = lockUser(email);
        CartItem item = ownedItem(id, user.getId());
        Product product = productDao.findVisibleProductById(item.getProduct().getId())
                .orElseThrow(() -> new BookNotFoundException("Book not found."));
        validateFormat(product, item.getFormat());
        long productQuantity = quantityForProduct(cartDao.findByUserId(user.getId()), product.getId())
                - item.getQuantity() + request.quantity();
        validateStock(product, productQuantity);
        item.setQuantity(request.quantity());
        cartDao.save(item);
        return cart(user.getId());
    }

    @Override
    public CartDto removeItem(String email, Long id) {
        User user = lockUser(email);
        cartDao.delete(ownedItem(id, user.getId()));
        return cart(user.getId());
    }

    private User lockUser(String email) {
        return userDao.findByEmailForUpdate(email).orElseThrow(() -> new UserNotFoundException("User not found."));
    }
    private CartItem ownedItem(Long id, Long userId) {
        return cartDao.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found."));
    }
    private long quantityForProduct(List<CartItem> items, Long productId) {
        return items.stream().filter(i -> i.getProduct().getId().equals(productId)).mapToLong(CartItem::getQuantity).sum();
    }
    private void validateFormat(Product product, BookFormat format) {
        if (!product.getFormats().contains(format))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This format is not available for this book.");
    }
    private void validateStock(Product product, long quantity) {
        if (quantity > product.getStockQuantity())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Requested quantity exceeds available stock across all formats.");
    }
    private CartDto cart(Long userId) {
        List<CartItem> entries = cartDao.findByUserId(userId);
        List<CartItemDto> items = entries.stream().map(item -> {
            Product p = item.getProduct();
            boolean available = p.isVisible() && p.getCategory().isVisible() && p.getFormats().contains(item.getFormat())
                    && quantityForProduct(entries, p.getId()) <= p.getStockQuantity();
            return new CartItemDto(item.getId(), p.getId(), p.getName(), p.getImages().isEmpty() ? null : p.getImages().getFirst(),
                    item.getFormat(), item.getQuantity(), p.getPrice(), p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())),
                    p.getStockQuantity(), available);
        }).toList();
        return new CartDto(items, items.stream().mapToLong(CartItemDto::quantity).sum(),
                items.stream().map(CartItemDto::lineTotal).reduce(new BigDecimal("0.00"), BigDecimal::add));
    }
}
