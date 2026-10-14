package se327.bookstore.dao;

import se327.bookstore.entity.User;

import java.util.Optional;

public interface UserDao {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
