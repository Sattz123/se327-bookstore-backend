package se327.bookstore.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dto.CheckoutRequest;
import se327.bookstore.dto.OrderDto;
import se327.bookstore.entity.*;
import se327.bookstore.repository.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=postgresql_checkout_integration_test",
        "spring.flyway.default-schema=postgresql_checkout_integration_test",
        "spring.flyway.schemas=postgresql_checkout_integration_test",
        "spring.jpa.properties.hibernate.default_schema=postgresql_checkout_integration_test"
})
@ActiveProfiles("test")
class PostgreSqlCheckoutIntegrationTests {

    @Autowired private CheckoutService checkout;
    @Autowired private UserRepository users;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private CartItemRepository carts;
    @Autowired private AddressRepository addresses;
    @Autowired private PaymentMethodRepository paymentMethods;
    @Autowired private OrderRepository orders;

    private final List<Long> userIds = new ArrayList<>();
    private final List<Long> productIds = new ArrayList<>();
    private final List<Long> categoryIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long id : userIds) {
            users.deleteById(id);
        }
        for (Long id : productIds) {
            products.deleteById(id);
        }
        for (Long id : categoryIds) {
            categories.deleteById(id);
        }
    }

    @Test
    void checkoutRejectsAddressOwnedByAnotherUser() {
        Product book = createBook("Ownership Book", new BigDecimal("29.95"), 10, BookFormat.PAPERBACK);
        User owner = createUser("address.owner@example.com", "Address Owner");
        Address foreignAddress = createAddress(owner, "100 Owner St", "Bangkok", "10110");

        User intruder = createUser("address.intruder@example.com", "Intruder User");
        addToCart(intruder, book, BookFormat.PAPERBACK, 1);

        CheckoutRequest request = new CheckoutRequest(
                foreignAddress.getId(),
                PaymentType.CASH_ON_DELIVERY,
                null,
                UUID.randomUUID(),
                checkout.review(intruder.getEmail()).cartFingerprint()
        );

        String intruderEmail = intruder.getEmail();
        assertThatThrownBy(() -> checkout.checkout(intruderEmail, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void checkoutRejectsPaymentMethodOwnedByAnotherUser() {
        Product book = createBook("Card Ownership Book", new BigDecimal("45.00"), 10, BookFormat.HARDCOVER);
        User owner = createUser("card.owner@example.com", "Card Owner");
        Address intruderAddress = createAddress(createUser("card.intruder@example.com", "Card Intruder"), "200 Intruder St", "Chiang Mai", "50200");
        PaymentMethod foreignCard = createPaymentMethod(owner, "Visa", "4242", 12, 2030);

        User intruder = intruderAddress.getUser();
        addToCart(intruder, book, BookFormat.HARDCOVER, 1);

        CheckoutRequest request = new CheckoutRequest(
                intruderAddress.getId(),
                PaymentType.SIMULATED_CARD,
                foreignCard.getId(),
                UUID.randomUUID(),
                checkout.review(intruder.getEmail()).cartFingerprint()
        );

        String intruderEmail = intruder.getEmail();
        assertThatThrownBy(() -> checkout.checkout(intruderEmail, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void orderConfirmationRejectsCrossUserOrderAccess() {
        Product book = createBook("Confidential Book", new BigDecimal("15.50"), 10, BookFormat.PAPERBACK);
        User customerA = createUser("customer.a@example.com", "Customer A");
        Address addressA = createAddress(customerA, "300 Alpha Rd", "Phuket", "83000");
        addToCart(customerA, book, BookFormat.PAPERBACK, 1);

        CheckoutRequest request = new CheckoutRequest(
                addressA.getId(),
                PaymentType.CASH_ON_DELIVERY,
                null,
                UUID.randomUUID(),
                checkout.review(customerA.getEmail()).cartFingerprint()
        );
        OrderDto order = checkout.checkout(customerA.getEmail(), request);

        User customerB = createUser("customer.b@example.com", "Customer B");
        String customerBEmail = customerB.getEmail();
        Long orderId = order.id();

        assertThatThrownBy(() -> checkout.confirmation(customerBEmail, orderId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void orderSnapshotsRemainIntactWhenCustomerAddressAndProductChangeLater() {
        Product book = createBook("Snapshot Book", new BigDecimal("20.00"), 5, BookFormat.PAPERBACK);
        User customer = createUser("snapshot.customer@example.com", "Snapshot Customer");
        Address address = createAddress(customer, "Original Street 123", "Bangkok", "10110");
        addToCart(customer, book, BookFormat.PAPERBACK, 2);

        CheckoutRequest request = new CheckoutRequest(
                address.getId(),
                PaymentType.CASH_ON_DELIVERY,
                null,
                UUID.randomUUID(),
                checkout.review(customer.getEmail()).cartFingerprint()
        );
        OrderDto placed = checkout.checkout(customer.getEmail(), request);

        // Mutate live address and product in database (reloading latest versions from DB)
        Address updatedAddress = addresses.findById(address.getId()).orElseThrow();
        updatedAddress.setAddressLine("Changed Avenue 999");
        addresses.saveAndFlush(updatedAddress);

        Product updatedBook = products.findById(book.getId()).orElseThrow();
        updatedBook.setPrice(new BigDecimal("99.99"));
        updatedBook.setName("Renamed Book Title");
        products.saveAndFlush(updatedBook);

        // Fetch confirmation from PostgreSQL and verify snapshot immutability
        OrderDto confirmation = checkout.confirmation(customer.getEmail(), placed.id());
        assertThat(confirmation.shippingAddress()).contains("Original Street 123");
        assertThat(confirmation.shippingAddress()).doesNotContain("Changed Avenue 999");
        assertThat(confirmation.total()).isEqualByComparingTo("40.00");
        assertThat(confirmation.items()).singleElement().satisfies(item -> {
            assertThat(item.title()).isEqualTo("Snapshot Book");
            assertThat(item.unitPrice()).isEqualByComparingTo("20.00");
            assertThat(item.lineTotal()).isEqualByComparingTo("40.00");
            assertThat(item.quantity()).isEqualTo(2);
        });
    }

    @Test
    void concurrentStockChangesWithMultipleFormatsAccuratelyDepleteSharedStock() throws Exception {
        // Book has 3 total stock shared between PAPERBACK and HARDCOVER
        Product book = createBook("Format Battle Book", new BigDecimal("30.00"), 3, BookFormat.PAPERBACK, BookFormat.HARDCOVER);

        User customerPaperback = createUser("buyer.paperback@example.com", "Paperback Buyer");
        Address addressPb = createAddress(customerPaperback, "1 PB Lane", "Bangkok", "10110");
        addToCart(customerPaperback, book, BookFormat.PAPERBACK, 2);

        User customerHardcover = createUser("buyer.hardcover@example.com", "Hardcover Buyer");
        Address addressHc = createAddress(customerHardcover, "2 HC Lane", "Bangkok", "10110");
        addToCart(customerHardcover, book, BookFormat.HARDCOVER, 2);

        CheckoutRequest requestPb = new CheckoutRequest(
                addressPb.getId(),
                PaymentType.CASH_ON_DELIVERY,
                null,
                UUID.randomUUID(),
                checkout.review(customerPaperback.getEmail()).cartFingerprint()
        );

        CheckoutRequest requestHc = new CheckoutRequest(
                addressHc.getId(),
                PaymentType.CASH_ON_DELIVERY,
                null,
                UUID.randomUUID(),
                checkout.review(customerHardcover.getEmail()).cartFingerprint()
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startSignal = new CountDownLatch(1);

        try {
            Future<Integer> task1 = executor.submit(() -> {
                startSignal.await();
                return tryCheckout(customerPaperback.getEmail(), requestPb);
            });
            Future<Integer> task2 = executor.submit(() -> {
                startSignal.await();
                return tryCheckout(customerHardcover.getEmail(), requestHc);
            });

            startSignal.countDown();

            List<Integer> statuses = List.of(task1.get(15, TimeUnit.SECONDS), task2.get(15, TimeUnit.SECONDS));
            // Exactly one succeeds (201) and one conflicts (409) because 2 + 2 = 4 > 3 stock
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);

            Product refreshedBook = products.findById(book.getId()).orElseThrow();
            // Initial 3 stock - 2 purchased = exactly 1 remaining
            assertThat(refreshedBook.getStockQuantity()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(15, TimeUnit.SECONDS);
        }
    }

    private int tryCheckout(String email, CheckoutRequest request) {
        try {
            checkout.checkout(email, request);
            return 201;
        } catch (ResponseStatusException ex) {
            return ex.getStatusCode().value();
        }
    }

    private Product createBook(String name, BigDecimal price, int stock, BookFormat... formats) {
        Category category = categories.saveAndFlush(Category.builder().name("Integration Category " + UUID.randomUUID()).build());
        categoryIds.add(category.getId());

        Product product = products.saveAndFlush(Product.builder()
                .category(category)
                .name(name)
                .price(price)
                .stockQuantity(stock)
                .formats(new HashSet<>(Arrays.asList(formats)))
                .build());
        productIds.add(product.getId());
        return product;
    }

    private User createUser(String email, String username) {
        User user = users.saveAndFlush(User.builder()
                .username(username)
                .email(email)
                .passwordHash("password-hash")
                .role(Role.CUSTOMER)
                .build());
        userIds.add(user.getId());
        return user;
    }

    private Address createAddress(User user, String line, String city, String postalCode) {
        return addresses.saveAndFlush(Address.builder()
                .user(user)
                .label("Home")
                .fullName(user.getUsername())
                .phone("0812345678")
                .addressLine(line)
                .city(city)
                .province(city)
                .postalCode(postalCode)
                .defaultAddress(true)
                .build());
    }

    private PaymentMethod createPaymentMethod(User user, String provider, String lastFour, int expMonth, int expYear) {
        return paymentMethods.saveAndFlush(PaymentMethod.builder()
                .user(user)
                .provider(provider)
                .cardholderName(user.getUsername())
                .lastFour(lastFour)
                .expiryMonth(expMonth)
                .expiryYear(expYear)
                .defaultMethod(true)
                .build());
    }

    private void addToCart(User user, Product product, BookFormat format, int quantity) {
        carts.saveAndFlush(CartItem.builder()
                .user(user)
                .product(product)
                .format(format)
                .quantity(quantity)
                .build());
    }
}
