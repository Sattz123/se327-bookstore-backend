package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.PasswordResetTokenDao;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.ForgotPasswordRequest;
import se327.bookstore.dto.MessageResponseDto;
import se327.bookstore.dto.ResetPasswordRequest;
import se327.bookstore.dto.VerifyResetTokenResponse;
import se327.bookstore.entity.PasswordResetToken;
import se327.bookstore.entity.User;
import se327.bookstore.exception.InvalidPasswordResetTokenException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class PasswordRecoveryServiceImpl implements PasswordRecoveryService {

    public static final String GENERIC_RESET_REQUEST_MESSAGE =
            "If an account with that email exists, password reset instructions have been sent.";
    public static final int TOKEN_EXPIRY_MINUTES = 30;

    private final UserDao userDao;
    private final PasswordResetTokenDao passwordResetTokenDao;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public MessageResponseDto requestPasswordReset(ForgotPasswordRequest request) {
        String email = request.email().strip().toLowerCase();
        Optional<User> userOptional = userDao.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();

            // Invalidate/delete any previous tokens for this user
            passwordResetTokenDao.deleteByUser(user);

            String token = UUID.randomUUID().toString();
            Instant expiryDate = Instant.now().plus(TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES);

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(token)
                    .user(user)
                    .expiryDate(expiryDate)
                    .used(false)
                    .build();

            passwordResetTokenDao.save(resetToken);
            emailService.sendPasswordResetEmail(user.getEmail(), token);
        }

        return new MessageResponseDto(GENERIC_RESET_REQUEST_MESSAGE);
    }

    @Override
    @Transactional(readOnly = true)
    public VerifyResetTokenResponse verifyResetToken(String token) {
        if (token == null || token.isBlank()) {
            return new VerifyResetTokenResponse(false, "Invalid password reset token.");
        }

        Optional<PasswordResetToken> tokenOptional = passwordResetTokenDao.findByToken(token.strip());
        if (tokenOptional.isEmpty()) {
            return new VerifyResetTokenResponse(false, "Invalid password reset token.");
        }

        PasswordResetToken resetToken = tokenOptional.get();
        if (resetToken.isUsed()) {
            return new VerifyResetTokenResponse(false, "Password reset token has already been used.");
        }

        if (resetToken.isExpired()) {
            return new VerifyResetTokenResponse(false, "Password reset token has expired.");
        }

        return new VerifyResetTokenResponse(true, "Token is valid.");
    }

    @Override
    public MessageResponseDto resetPassword(ResetPasswordRequest request) {
        String tokenString = request.token().strip();
        PasswordResetToken resetToken = passwordResetTokenDao.findByToken(tokenString)
                .orElseThrow(() -> new InvalidPasswordResetTokenException("Invalid password reset token."));

        if (resetToken.isUsed()) {
            throw new InvalidPasswordResetTokenException("Password reset token has already been used.");
        }

        if (resetToken.isExpired()) {
            throw new InvalidPasswordResetTokenException("Password reset token has expired.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword().strip()));
        userDao.save(user);

        resetToken.setUsed(true);
        passwordResetTokenDao.save(resetToken);

        return new MessageResponseDto("Password has been reset successfully.");
    }
}
