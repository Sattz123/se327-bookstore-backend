package se327.bookstore.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.repository.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=cart_persistence_test", "spring.flyway.default-schema=cart_persistence_test",
        "spring.flyway.schemas=cart_persistence_test", "spring.jpa.properties.hibernate.default_schema=cart_persistence_test"
})
@ActiveProfiles("test") @Transactional
class CartPersistenceTests {
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private CartItemRepository items;
    @Autowired private JdbcTemplate jdbc;
    private User customer;
    private Product book;

    @BeforeEach void setup() {
        customer = user("cart.storage@example.com");
        Category category = categories.saveAndFlush(Category.builder().name("Cart storage books").build());
        book = products.saveAndFlush(Product.builder().category(category).name("Storage book").price(BigDecimal.TEN)
                .stockQuantity(10).formats(new HashSet<>(Set.of(BookFormat.PAPERBACK, BookFormat.HARDCOVER))).build());
    }
    @Test void databaseRejectsDuplicateUserProductAndFormat() {
        save(customer, BookFormat.PAPERBACK);
        assertThatThrownBy(() -> save(customer, BookFormat.PAPERBACK)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void differentUsersAndFormatsHaveIndependentRows() {
        save(customer, BookFormat.PAPERBACK);
        save(customer, BookFormat.HARDCOVER);
        save(user("other.cart.storage@example.com"), BookFormat.PAPERBACK);
        assertThat(items.findByUserIdOrderByIdAsc(customer.getId())).hasSize(2);
    }
    @Test void databaseRejectsZeroQuantity() {
        CartItem item = save(customer, BookFormat.PAPERBACK);
        long itemId = item.getId();
        assertThatThrownBy(() -> jdbc.update("UPDATE cart_items SET quantity = 0 WHERE id = ?", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void deletingAUserRemovesOnlyTheirCart() {
        User other = user("other.cart.storage@example.com");
        save(customer, BookFormat.PAPERBACK);
        save(other, BookFormat.PAPERBACK);
        jdbc.update("DELETE FROM users WHERE id = ?", customer.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE user_id = ?", Integer.class, customer.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE user_id = ?", Integer.class, other.getId())).isEqualTo(1);
    }
    private User user(String email) {
        return users.saveAndFlush(User.builder().username("Cart reader").email(email).passwordHash("test-hash").role(Role.CUSTOMER).build());
    }
    private CartItem save(User owner, BookFormat format) {
        return items.saveAndFlush(CartItem.builder().user(owner).product(book).format(format).quantity(1).build());
    }
}
