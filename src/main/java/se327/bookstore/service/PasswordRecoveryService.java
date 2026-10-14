package se327.bookstore.service;

import se327.bookstore.dto.ForgotPasswordRequest;
import se327.bookstore.dto.MessageResponseDto;
import se327.bookstore.dto.ResetPasswordRequest;
import se327.bookstore.dto.VerifyResetTokenResponse;

public interface PasswordRecoveryService {

    MessageResponseDto requestPasswordReset(ForgotPasswordRequest request);

    VerifyResetTokenResponse verifyResetToken(String token);

    MessageResponseDto resetPassword(ResetPasswordRequest request);
}
