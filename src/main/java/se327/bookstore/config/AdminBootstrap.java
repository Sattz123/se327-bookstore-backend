package se327.bookstore.config;

import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.RegisterRequest;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "app.admin.bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
    private final UserDao users;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final String email;
    private final String password;
    private final String username;

    public AdminBootstrap(UserDao users, PasswordEncoder encoder, Validator validator,
                          @Value("${app.admin.bootstrap.email:}") String email,
                          @Value("${app.admin.bootstrap.password:}") String password,
                          @Value("${app.admin.bootstrap.username:Administrator}") String username) {
        this.users = users;
        this.encoder = encoder;
        this.validator = validator;
        this.email = email.strip().toLowerCase(Locale.ROOT);
        this.password = password;
        this.username = username.strip();
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (!validator.validate(new RegisterRequest(username, email, password, null)).isEmpty())
            throw new IllegalStateException("Admin bootstrap requires a valid email, username (2–100 characters), and password (6–100 characters).");
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() != Role.ADMIN)
                throw new IllegalStateException("Admin bootstrap cannot promote an existing customer account. Choose a separate administrator email.");
            return; // Existing administrators keep their current password and profile.
        }
        users.save(User.builder().username(username).email(email).passwordHash(encoder.encode(password)).role(Role.ADMIN).build());
    }
}
