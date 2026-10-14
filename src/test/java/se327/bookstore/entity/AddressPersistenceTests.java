package se327.bookstore.entity;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.repository.AddressRepository;
import se327.bookstore.repository.UserRepository;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=address_persistence_test",
        "spring.flyway.default-schema=address_persistence_test",
        "spring.flyway.schemas=address_persistence_test",
        "spring.jpa.properties.hibernate.default_schema=address_persistence_test"
})
@ActiveProfiles("test")
@Transactional
class AddressPersistenceTests {
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void databaseRejectsTwoDefaultsForTheSameUser() {
        User user = user("same@example.com");
        address(user, true);
        assertThatThrownBy(() -> address(user, true)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersCanEachHaveADefaultAndAdditionalAddresses() {
        User first = user("first@example.com");
        User second = user("second@example.com");
        Address saved = address(first, true);
        address(first, false);
        address(second, true);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(first.getId())).hasSize(2);
    }

    @Test
    void deletingAUserCascadesOnlyTheirAddresses() {
        User first = user("first@example.com");
        User second = user("second@example.com");
        address(first, false);
        address(second, true);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", first.getId());
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM addresses WHERE user_id = ?", Integer.class, first.getId())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM addresses WHERE user_id = ?", Integer.class, second.getId())).isEqualTo(1);
    }

    private User user(String email) {
        return userRepository.saveAndFlush(User.builder().username("Reader").email(email).passwordHash("test-hash").role(Role.CUSTOMER).build());
    }

    private Address address(User user, boolean isDefault) {
        return addressRepository.saveAndFlush(Address.builder().user(user).label("Home").fullName("Reader")
                .addressLine("Street").defaultAddress(isDefault).build());
    }
}
