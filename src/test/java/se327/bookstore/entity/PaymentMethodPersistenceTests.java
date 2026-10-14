package se327.bookstore.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.repository.PaymentMethodRepository;
import se327.bookstore.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=payment_persistence_test",
        "spring.flyway.default-schema=payment_persistence_test",
        "spring.flyway.schemas=payment_persistence_test",
        "spring.jpa.properties.hibernate.default_schema=payment_persistence_test"
})
@ActiveProfiles("test")
@Transactional
class PaymentMethodPersistenceTests {
    @Autowired private UserRepository userRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void databaseRejectsTwoDefaultsForTheSameUser() {
        User user = user("same@example.com");
        paymentMethod(user, true);
        assertThatThrownBy(() -> paymentMethod(user, true)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersCanEachHaveADefaultAndAdditionalPaymentMethods() {
        User first = user("first@example.com");
        User second = user("second@example.com");
        PaymentMethod saved = paymentMethod(first, true);
        paymentMethod(first, false);
        paymentMethod(second, true);
        assertThat(saved.getCreatedAt()).isNotNull();

        assertThat(paymentMethodRepository.findByUserIdOrderByDefaultMethodDescCreatedAtDescIdDesc(first.getId())).hasSize(2);
    }

    @Test
    void deletingAUserCascadesOnlyTheirPaymentMethods() {
        User first = user("first@example.com");
        User second = user("second@example.com");
        paymentMethod(first, false);
        paymentMethod(second, true);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", first.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payment_methods WHERE user_id = ?", Integer.class, first.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM payment_methods WHERE user_id = ?", Integer.class, second.getId())).isEqualTo(1);
    }

    private User user(String email) {
        return userRepository.saveAndFlush(User.builder().username("Reader").email(email).passwordHash("test-hash").role(Role.CUSTOMER).build());
    }

    private PaymentMethod paymentMethod(User user, boolean isDefault) {
        return paymentMethodRepository.saveAndFlush(PaymentMethod.builder().user(user).provider("Visa").cardholderName("Reader").lastFour("1234").expiryMonth(12).expiryYear(2030)
                .defaultMethod(isDefault).build());
    }
}


