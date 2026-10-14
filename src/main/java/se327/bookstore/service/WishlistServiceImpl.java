package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dao.WishlistDao;
import se327.bookstore.dto.AddToWishlistRequest;
import se327.bookstore.dto.CategoryDto;
import se327.bookstore.dto.ProductDto;
import se327.bookstore.dto.WishlistItemDto;
import se327.bookstore.entity.Product;
import se327.bookstore.entity.User;
import se327.bookstore.entity.WishlistItem;
import se327.bookstore.exception.BookNotFoundException;
import se327.bookstore.exception.DuplicateWishlistItemException;
import se327.bookstore.exception.UserNotFoundException;
import se327.bookstore.exception.WishlistItemNotFoundException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WishlistServiceImpl implements WishlistService {

    private final WishlistDao wishlistDao;
    private final UserDao userDao;
    private final ProductDao productDao;

    @Override
    @Transactional(readOnly = true)
    public List<WishlistItemDto> getWishlistForUser(String userEmail) {
        User user = findUserByEmail(userEmail);
        List<WishlistItem> items = wishlistDao.findByUserId(user.getId());

        return items.stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public WishlistItemDto addToWishlist(String userEmail, AddToWishlistRequest request) {
        User user = findUserByEmail(userEmail);
        Long productId = request.productId();

        Product product = productDao.findVisibleProductById(productId)
                .orElseThrow(() -> new BookNotFoundException("Book not found."));

        if (wishlistDao.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new DuplicateWishlistItemException("Book is already in your wishlist.");
        }

        WishlistItem item = WishlistItem.builder()
                .user(user)
                .product(product)
                .build();

        WishlistItem saved = wishlistDao.save(item);
        return toDto(saved);
    }

    @Override
    public void removeFromWishlist(String userEmail, Long productId) {
        User user = findUserByEmail(userEmail);

        WishlistItem item = wishlistDao.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new WishlistItemNotFoundException("Book is not in your wishlist."));

        wishlistDao.delete(item);
    }

    private User findUserByEmail(String email) {
        return userDao.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    private WishlistItemDto toDto(WishlistItem item) {
        return new WishlistItemDto(
                item.getId(),
                toProductDto(item.getProduct()),
                item.getCreatedAt()
        );
    }

    private ProductDto toProductDto(Product product) {
        var category = product.getCategory();
        CategoryDto categoryDto = category != null
                ? new CategoryDto(category.getId(), category.getName(), category.getImageUrl())
                : null;

        return new ProductDto(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getAuthor(),
                product.getStockQuantity(),
                categoryDto,
                product.getImages() != null ? List.copyOf(product.getImages()) : List.of(),
                product.getFormats() != null ? product.getFormats().stream().sorted().toList() : List.of()
        );
    }
}
