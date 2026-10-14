package se327.bookstore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class DevelopmentEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentEmailService.class);

    private final String frontendOrigin;
    private final List<SentEmailRecord> sentEmails = new CopyOnWriteArrayList<>();

    public record SentEmailRecord(
            String recipient,
            String subject,
            String resetLink,
            String token,
            Instant sentAt
    ) {
    }

    public DevelopmentEmailService(@Value("${app.frontend-origin}") String frontendOrigin) {
        this.frontendOrigin = frontendOrigin;
    }

    @Override
    public void sendPasswordResetEmail(String recipientEmail, String resetToken) {
        String resetLink = frontendOrigin + "/reset-password?token=" + resetToken;
        String subject = "InwZa Bookstore - Password Reset Request";

        log.info("""
                
                ============================================================
                [DEVELOPMENT EMAIL SERVICE]
                To:         {}
                Subject:    {}
                Reset Link: {}
                Token:      {}
                ============================================================
                """, recipientEmail, subject, resetLink, resetToken);

        sentEmails.add(new SentEmailRecord(recipientEmail, subject, resetLink, resetToken, Instant.now()));
    }

    public List<SentEmailRecord> getSentEmails() {
        return List.copyOf(sentEmails);
    }

    public Optional<SentEmailRecord> findLatestEmailByRecipient(String recipientEmail) {
        for (int i = sentEmails.size() - 1; i >= 0; i--) {
            SentEmailRecord record = sentEmails.get(i);
            if (record.recipient().equalsIgnoreCase(recipientEmail)) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    public void clear() {
        sentEmails.clear();
    }
}
