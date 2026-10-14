package se327.bookstore.dao;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import se327.bookstore.entity.PasswordResetToken;
import se327.bookstore.entity.User;
import se327.bookstore.repository.PasswordResetTokenRepository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenDaoImpl implements PasswordResetTokenDao {

    private final PasswordResetTokenRepository passwordResetTokenRepository;

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        return passwordResetTokenRepository.save(token);
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return passwordResetTokenRepository.findByToken(token);
    }

    @Override
    public void deleteByUser(User user) {
        passwordResetTokenRepository.deleteByUser(user);
    }
}
