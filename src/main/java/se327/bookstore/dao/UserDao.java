package se327.bookstore.dao;

import se327.bookstore.entity.User;

import java.util.Optional;

public interface UserDao {

    User save(User user);

    Optional<User> findById(Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailForUpdate(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    org.springframework.data.domain.Page<se327.bookstore.dto.AdminCustomerDto> findCustomers(String query, org.springframework.data.domain.Pageable pageable);
}
