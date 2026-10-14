package se327.bookstore.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.CartDao;
import se327.bookstore.dto.CheckoutRequest;
import se327.bookstore.entity.*;
import se327.bookstore.repository.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=checkout_transaction_test", "spring.flyway.default-schema=checkout_transaction_test",
        "spring.flyway.schemas=checkout_transaction_test", "spring.jpa.properties.hibernate.default_schema=checkout_transaction_test"
})
@ActiveProfiles("test")
class CheckoutTransactionTests {
    @Autowired private CheckoutService checkout;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private CartItemRepository carts;
    @Autowired private AddressRepository addresses;
    @Autowired private OrderRepository orders;
    @MockitoSpyBean private CartDao cartDao;
    private final List<Long> userIds = new ArrayList<>();
    private Product book;
    private Category category;

    @AfterEach void cleanup() {
        for (Long id : userIds) users.deleteById(id);
        if (book != null) products.deleteById(book.getId());
        if (category != null) categories.deleteById(category.getId());
    }

    @Test void competingCustomersCannotBuyTheSameLastCopy() throws Exception {
        createBook(1);
        User first = customer("first.transaction@example.com");
        User second = customer("second.transaction@example.com");
        var firstRequest = request(first);
        var secondRequest = request(second);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var one = executor.submit(() -> { start.await(); return attempt(first, firstRequest); });
            var two = executor.submit(() -> { start.await(); return attempt(second, secondRequest); });
            start.countDown();
            assertThat(new Integer[]{one.get(15, TimeUnit.SECONDS), two.get(15, TimeUnit.SECONDS)})
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(orders.count()).isEqualTo(1);
            assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isZero();
            assertThat(carts.count()).isEqualTo(1);
        } finally { executor.shutdownNow(); executor.awaitTermination(15, TimeUnit.SECONDS); }
    }

    @Test void simultaneousRetriesCreateOnlyOneOrderAndDeductStockOnce() throws Exception {
        createBook(2);
        User user = customer("duplicate.transaction@example.com");
        var request = request(user);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Long> call = () -> { start.await(); return checkout.checkout(user.getEmail(), request).id(); };
            var one = executor.submit(call);
            var two = executor.submit(call);
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS)).isEqualTo(two.get(15, TimeUnit.SECONDS));
            assertThat(orders.count()).isEqualTo(1);
            assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(1);
            assertThat(carts.count()).isZero();
        } finally { executor.shutdownNow(); executor.awaitTermination(15, TimeUnit.SECONDS); }
    }

    @Test void aFailureAfterSavingTheOrderAndDeletingTheCartRollsBackEveryWrite() {
        createBook(2);
        User user = customer("rollback.transaction@example.com");
        var request = request(user);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Injected failure after cart deletion");
        }).when(cartDao).delete(any(CartItem.class));
        assertThatThrownBy(() -> checkout.checkout(user.getEmail(), request))
                .hasRootCauseInstanceOf(IllegalStateException.class).hasMessageContaining("Injected failure after cart deletion");
        assertThat(orders.count()).isZero();
        assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(2);
        assertThat(carts.findByUserIdOrderByIdAsc(user.getId())).singleElement().extracting(CartItem::getQuantity).isEqualTo(1);
    }

    private int attempt(User user, CheckoutRequest request) {
        try { checkout.checkout(user.getEmail(), request); return 201; }
        catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
    }
    private void createBook(int stock) {
        category = categories.saveAndFlush(Category.builder().name("Transaction books").build());
        book = products.saveAndFlush(Product.builder().category(category).name("Shared book").price(new BigDecimal("19.95"))
                .stockQuantity(stock).formats(new HashSet<>(Set.of(BookFormat.PAPERBACK))).build());
    }
    private User customer(String email) {
        User user = users.saveAndFlush(User.builder().username("Transaction reader").email(email).passwordHash("test-hash")
                .role(Role.CUSTOMER).build());
        userIds.add(user.getId());
        carts.saveAndFlush(CartItem.builder().user(user).product(book).format(BookFormat.PAPERBACK).quantity(1).build());
        addresses.saveAndFlush(Address.builder().user(user).label("Home").fullName("Reader").addressLine("Test street").build());
        return user;
    }
    private CheckoutRequest request(User user) {
        Long addressId = addresses.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(user.getId()).getFirst().getId();
        return new CheckoutRequest(addressId, PaymentType.CASH_ON_DELIVERY, null, UUID.randomUUID(), checkout.review(user.getEmail()).cartFingerprint());
    }
}
