package se327.bookstore.service;

public interface EmailService {

    void sendPasswordResetEmail(String recipientEmail, String resetToken);
}
