package se327.bookstore.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dto.AddToCartRequest;
import se327.bookstore.entity.*;
import se327.bookstore.repository.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=cart_concurrency_test", "spring.flyway.default-schema=cart_concurrency_test",
        "spring.flyway.schemas=cart_concurrency_test", "spring.jpa.properties.hibernate.default_schema=cart_concurrency_test"
})
@ActiveProfiles("test")
class CartConcurrencyTests {
    @Autowired private CartService service;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private CartItemRepository items;

    @ParameterizedTest @ValueSource(ints = {1, 10})
    void simultaneousAddsDoNotLoseQuantitiesOrExceedStock(int stock) throws Exception {
        User user = users.saveAndFlush(User.builder().username("Concurrent cart").email("concurrent.cart@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        Category category = categories.saveAndFlush(Category.builder().name("Concurrency books").build());
        Product book = products.saveAndFlush(Product.builder().category(category).name("Concurrent book").price(BigDecimal.TEN)
                .stockQuantity(stock).formats(new HashSet<>(Set.of(BookFormat.PAPERBACK))).build());
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Integer> add = () -> {
            start.await();
            try { service.addItem(user.getEmail(), new AddToCartRequest(book.getId(), BookFormat.PAPERBACK, 1)); return 201; }
            catch (ResponseStatusException error) { return error.getStatusCode().value(); }
        };
        try {
            var one = executor.submit(add);
            var two = executor.submit(add);
            start.countDown();
            assertThat(new Integer[]{one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(stock == 1 ? new Integer[]{201, 409} : new Integer[]{201, 201});
            assertThat(items.findByUserIdOrderByIdAsc(user.getId())).singleElement()
                    .extracting(CartItem::getQuantity).isEqualTo(stock == 1 ? 1 : 2);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            users.deleteById(user.getId());
            products.deleteById(book.getId());
            categories.deleteById(category.getId());
        }
    }
}
