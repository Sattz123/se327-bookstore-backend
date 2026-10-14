package se327.bookstore.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.WishlistDao;
import se327.bookstore.repository.CategoryRepository;
import se327.bookstore.repository.ProductRepository;
import se327.bookstore.repository.UserRepository;
import se327.bookstore.repository.WishlistItemRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WishlistPersistenceTests {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private WishlistDao wishlistDao;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private User createTestUser(String username, String email) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash("hashedpassword123")
                .role(Role.CUSTOMER)
                .build();
        return userRepository.save(user);
    }

    private Product createTestProduct(String name) {
        Category category = categoryRepository.findAll().stream().findFirst().orElseGet(() ->
                categoryRepository.save(Category.builder().name("Default Category").build())
        );

        Product product = Product.builder()
                .name(name)
                .description("Sample test description")
                .price(new BigDecimal("19.99"))
                .stockQuantity(5)
                .visible(true)
                .category(category)
                .build();
        return productRepository.save(product);
    }

    @Test
    void persistsWishlistItemWithCreatedAtTimestamp() {
        User user = createTestUser("Alice Wish", "alice.wish@example.com");
        Product product = createTestProduct("Effective Java");

        WishlistItem item = WishlistItem.builder()
                .user(user)
                .product(product)
                .build();

        entityManager.persist(item);
        entityManager.flush();
        entityManager.clear();

        WishlistItem saved = entityManager.find(WishlistItem.class, item.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getUser().getId()).isEqualTo(user.getId());
        assertThat(saved.getProduct().getId()).isEqualTo(product.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void enforcesUniqueConstraintOnUserAndProduct() {
        User user = createTestUser("Bob Wish", "bob.wish@example.com");
        Product product = createTestProduct("Clean Code");

        WishlistItem item1 = WishlistItem.builder().user(user).product(product).build();
        wishlistItemRepository.saveAndFlush(item1);

        WishlistItem duplicate = WishlistItem.builder().user(user).product(product).build();
        assertThatThrownBy(() -> wishlistItemRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameProductForDifferentUsers() {
        User user1 = createTestUser("User One", "user1@example.com");
        User user2 = createTestUser("User Two", "user2@example.com");
        Product product = createTestProduct("Shared Book");

        WishlistItem item1 = WishlistItem.builder().user(user1).product(product).build();
        WishlistItem item2 = WishlistItem.builder().user(user2).product(product).build();

        wishlistItemRepository.saveAndFlush(item1);
        wishlistItemRepository.saveAndFlush(item2);

        assertThat(wishlistDao.existsByUserIdAndProductId(user1.getId(), product.getId())).isTrue();
        assertThat(wishlistDao.existsByUserIdAndProductId(user2.getId(), product.getId())).isTrue();
    }

    @Test
    void allowsMultipleDifferentProductsForSameUser() {
        User user = createTestUser("Multi Saver", "multi@example.com");
        Product product1 = createTestProduct("Book 1");
        Product product2 = createTestProduct("Book 2");

        wishlistDao.save(WishlistItem.builder().user(user).product(product1).build());
        wishlistDao.save(WishlistItem.builder().user(user).product(product2).build());

        List<WishlistItem> items = wishlistDao.findByUserId(user.getId());
        assertThat(items).hasSize(2);
    }

    @Test
    void deletesWishlistItemWithoutAffectingUserOrProduct() {
        User user = createTestUser("Delete Tester", "deletetester@example.com");
        Product product = createTestProduct("Book To Remove");

        WishlistItem item = wishlistDao.save(WishlistItem.builder().user(user).product(product).build());
        assertThat(wishlistDao.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue();

        wishlistDao.delete(item);
        entityManager.flush();

        assertThat(wishlistDao.existsByUserIdAndProductId(user.getId(), product.getId())).isFalse();
        assertThat(userRepository.findById(user.getId())).isPresent();
        assertThat(productRepository.findById(product.getId())).isPresent();
    }
}
