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
}
