package se327.bookstore.dao;

import se327.bookstore.entity.PasswordResetToken;
import se327.bookstore.entity.User;

import java.util.Optional;

public interface PasswordResetTokenDao {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByUser(User user);
}
