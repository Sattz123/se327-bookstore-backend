package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.User;
import se327.bookstore.repository.UserRepository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class UserDaoImpl implements UserDao {

    private final UserRepository userRepository;

    @Override
    public org.springframework.data.domain.Page<se327.bookstore.dto.AdminCustomerDto> findCustomers(String query, org.springframework.data.domain.Pageable pageable) {
        return userRepository.findCustomerSummaries(query, pageable).map(c -> new se327.bookstore.dto.AdminCustomerDto(
                c.getId(), c.getUsername(), c.getEmail(), c.getPhone(), c.getCreatedAt(), c.getOrderCount(), c.getTotalSpent()));
    }

    @Override
    public User save(User user) {
        return userRepository.save(user);
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(email.strip());
    }

    @Override
    public boolean existsByEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return userRepository.existsByEmailIgnoreCase(email.strip());
    }

    @Override
    public Optional<User> findByEmailForUpdate(String email) {
        return userRepository.findByEmailForUpdate(email.strip());
    }

    @Override
    public boolean existsByEmailAndIdNot(String email, Long id) {
        if (email == null || email.isBlank() || id == null) {
            return false;
        }
        return userRepository.existsByEmailIgnoreCaseAndIdNot(email.strip(), id);
    }
}
