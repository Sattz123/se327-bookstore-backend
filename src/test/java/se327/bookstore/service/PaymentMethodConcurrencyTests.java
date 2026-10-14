package se327.bookstore.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import se327.bookstore.entity.PaymentMethod;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.repository.PaymentMethodRepository;
import se327.bookstore.repository.UserRepository;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=payment_concurrency_test",
        "spring.flyway.default-schema=payment_concurrency_test",
        "spring.flyway.schemas=payment_concurrency_test",
        "spring.jpa.properties.hibernate.default_schema=payment_concurrency_test"
})
@ActiveProfiles("test")
class PaymentMethodConcurrencyTests {
    @Autowired private PaymentMethodService paymentMethodService;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private UserRepository userRepository;

    @Test
    void concurrentDefaultChangesBothSucceedAndLeaveExactlyOneDefault() throws Exception {
        User user = userRepository.saveAndFlush(User.builder().username("Concurrent reader")
                .email("concurrent.paymentMethod@example.com").passwordHash("test-hash").role(Role.CUSTOMER).build());
        var executor = Executors.newFixedThreadPool(2);
        try {
            PaymentMethod first = savePaymentMethod(user, "First");
            PaymentMethod second = savePaymentMethod(user, "Second");
            var start = new CountDownLatch(1);
            var one = executor.submit(() -> { start.await(); return paymentMethodService.setDefault(user.getEmail(), first.getId()); });
            var two = executor.submit(() -> { start.await(); return paymentMethodService.setDefault(user.getEmail(), second.getId()); });
            start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS).isDefault()).isTrue();
            assertThat(two.get(15, TimeUnit.SECONDS).isDefault()).isTrue();
            assertThat(paymentMethodRepository.findByUserIdOrderByDefaultMethodDescCreatedAtDescIdDesc(user.getId()))
                    .hasSize(2).filteredOn(PaymentMethod::isDefaultMethod).hasSize(1);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(15, TimeUnit.SECONDS);
            userRepository.deleteById(user.getId());
        }
    }

    private PaymentMethod savePaymentMethod(User user, String label) {
        return paymentMethodRepository.saveAndFlush(PaymentMethod.builder().user(user).provider(label).lastFour("1234").expiryMonth(12).expiryYear(2030)
                .cardholderName("Reader").build());
    }
}


