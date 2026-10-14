package se327.bookstore.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.repository.OrderRepository;
import se327.bookstore.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=order_storage_test", "spring.flyway.default-schema=order_storage_test",
        "spring.flyway.schemas=order_storage_test", "spring.jpa.properties.hibernate.default_schema=order_storage_test"
})
@ActiveProfiles("test") @Transactional
class OrderPersistenceTests {
    @Autowired private OrderRepository orders;
    @Autowired private UserRepository users;
    @Autowired private EntityManager em;

    @Test void preservesOrderSnapshotsWithoutDependingOnLiveProductsOrAddresses() {
        PurchaseOrder order = order();
        order.getItems().add(OrderItem.builder().order(order).productId(123L).title("Purchased title")
                .format(BookFormat.HARDCOVER).quantity(2).unitPrice(new BigDecimal("19.95"))
                .lineTotal(new BigDecimal("39.90")).build());
        Long id = orders.saveAndFlush(order).getId();
        em.clear();
        PurchaseOrder saved = orders.findById(id).orElseThrow();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getShippingAddress()).isEqualTo("123 Test Street");
        assertThat(saved.getTotal()).isEqualByComparingTo("39.90");
        assertThat(saved.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).isEqualTo("Purchased title");
            assertThat(item.getFormat()).isEqualTo(BookFormat.HARDCOVER);
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getUnitPrice()).isEqualByComparingTo("19.95");
        });
    }
    @Test void databaseEnforcesOneOrderPerCustomerAndCheckoutKey() {
        PurchaseOrder first = orders.saveAndFlush(order());
        PurchaseOrder duplicate = PurchaseOrder.builder().user(first.getUser()).checkoutKey(first.getCheckoutKey())
                .requestHash(first.getRequestHash()).status(first.getStatus()).paymentType(first.getPaymentType())
                .paymentDisplay(first.getPaymentDisplay()).recipientName(first.getRecipientName())
                .shippingAddress(first.getShippingAddress()).total(first.getTotal()).build();
        assertThatThrownBy(() -> orders.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
    private PurchaseOrder order() {
        User user = users.saveAndFlush(User.builder().username("Order reader").email("order.storage@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        return PurchaseOrder.builder().user(user).checkoutKey(UUID.randomUUID()).requestHash("a".repeat(64))
                .status(OrderStatus.PENDING).paymentType(PaymentType.CASH_ON_DELIVERY).paymentDisplay("Cash on delivery")
                .recipientName("Reader").shippingAddress("123 Test Street").total(new BigDecimal("39.90")).build();
    }
}
