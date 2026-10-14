package se327.bookstore.entity;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.UserDao;
import se327.bookstore.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class UserPersistenceTests {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserDao userDao;

    @Test
    void persistsCustomerAndAdminWithTimestampsAndRoles() {
        User customer = User.builder()
                .username("Alice")
                .email("alice@example.com")
                .passwordHash("$2a$10$hashedpasswordstringforalice1234567890")
                .phone("0812345678")
                .avatarUrl("https://example.com/alice.png")
                .role(Role.CUSTOMER)
                .build();

        entityManager.persist(customer);
        entityManager.flush();
        entityManager.clear();

        User saved = entityManager.find(User.class, customer.getId());
        assertThat(saved).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("Alice");
        assertThat(saved.getEmail()).isEqualTo("alice@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("$2a$10$hashedpasswordstringforalice1234567890");
        assertThat(saved.getPhone()).isEqualTo("0812345678");
        assertThat(saved.getAvatarUrl()).isEqualTo("https://example.com/alice.png");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void enforcesUniqueEmailConstraint() {
        User user1 = User.builder()
                .username("Bob")
                .email("bob@example.com")
                .passwordHash("hashedpassword1")
                .role(Role.CUSTOMER)
                .build();
        userRepository.saveAndFlush(user1);

        User user2 = User.builder()
                .username("Bob Duplicate")
                .email("bob@example.com")
                .passwordHash("hashedpassword2")
                .role(Role.ADMIN)
                .build();

        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsByEmailCaseInsensitivelyViaDaoAndRepository() {
        User user = User.builder()
                .username("Charlie")
                .email("charlie@example.com")
                .passwordHash("hashedpassword")
                .role(Role.ADMIN)
                .build();
        userDao.save(user);

        Optional<User> foundUpper = userDao.findByEmail("CHARLIE@EXAMPLE.COM");
        assertThat(foundUpper).isPresent();
        assertThat(foundUpper.get().getUsername()).isEqualTo("Charlie");
        assertThat(foundUpper.get().getRole()).isEqualTo(Role.ADMIN);

        assertThat(userDao.existsByEmail("charlie@EXAMPLE.com")).isTrue();
        assertThat(userDao.existsByEmail("unknown@example.com")).isFalse();
        assertThat(userDao.existsByEmail(null)).isFalse();
        assertThat(userDao.findByEmail(null)).isEmpty();
    }
}
