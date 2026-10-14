package se327.bookstore.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.PasswordResetTokenDao;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.ForgotPasswordRequest;
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.dto.ResetPasswordRequest;
import se327.bookstore.entity.PasswordResetToken;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.service.DevelopmentEmailService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordRecoveryControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordResetTokenDao passwordResetTokenDao;

    @Autowired
    private DevelopmentEmailService emailService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        emailService.clear();
    }

    @Test
    void requestsPasswordResetForExistingUserWithGenericResponseAndSentEmail() throws Exception {
        User user = User.builder()
                .username("Alice Recovery")
                .email("alice.recovery@example.com")
                .passwordHash(passwordEncoder.encode("InitialPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        ForgotPasswordRequest request = new ForgotPasswordRequest("alice.recovery@example.com");

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account with that email exists, password reset instructions have been sent."));

        Optional<DevelopmentEmailService.SentEmailRecord> emailRecord =
                emailService.findLatestEmailByRecipient("alice.recovery@example.com");
        assertThat(emailRecord).isPresent();
        assertThat(emailRecord.get().token()).isNotBlank();
        assertThat(emailRecord.get().resetLink()).contains(emailRecord.get().token());

        Optional<PasswordResetToken> token = passwordResetTokenDao.findByToken(emailRecord.get().token());
        assertThat(token).isPresent();
        assertThat(token.get().getUser().getEmail()).isEqualTo("alice.recovery@example.com");
        assertThat(token.get().isUsed()).isFalse();
        assertThat(token.get().isExpired()).isFalse();
    }

    @Test
    void requestsPasswordResetForNonExistentUserReturnsGenericResponseWithoutSendingEmail() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest("unknown.user@example.com");

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If an account with that email exists, password reset instructions have been sent."));

        assertThat(emailService.getSentEmails()).isEmpty();
    }

    @Test
    void requestingPasswordResetAgainInvalidatesPreviousToken() throws Exception {
        User user = User.builder()
                .username("Multiple Requests")
                .email("multiple.reset@example.com")
                .passwordHash(passwordEncoder.encode("InitialPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        ForgotPasswordRequest request = new ForgotPasswordRequest("multiple.reset@example.com");

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        String firstToken = emailService.findLatestEmailByRecipient("multiple.reset@example.com")
                .orElseThrow().token();

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        String secondToken = emailService.findLatestEmailByRecipient("multiple.reset@example.com")
                .orElseThrow().token();

        assertThat(firstToken).isNotEqualTo(secondToken);
        assertThat(passwordResetTokenDao.findByToken(firstToken)).isEmpty();
        assertThat(passwordResetTokenDao.findByToken(secondToken)).isPresent();
    }

    @Test
    void verifiesValidTokenSuccessfully() throws Exception {
        User user = User.builder()
                .username("Verifier")
                .email("verifier@example.com")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .token("valid-test-token-12345")
                .user(user)
                .expiryDate(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenDao.save(token);

        mvc.perform(get("/api/auth/verify-reset-token")
                        .param("token", "valid-test-token-12345"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Token is valid."));
    }

    @Test
    void verifiesInvalidOrExpiredTokenReturnsValidFalse() throws Exception {
        mvc.perform(get("/api/auth/verify-reset-token")
                        .param("token", "non-existent-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Invalid password reset token."));
    }

    @Test
    void resetsPasswordSuccessfullyAndAllowsLoginWithNewPassword() throws Exception {
        User user = User.builder()
                .username("Resetter")
                .email("resetter@example.com")
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .token("usable-reset-token-999")
                .user(user)
                .expiryDate(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenDao.save(token);

        ResetPasswordRequest resetRequest = new ResetPasswordRequest("usable-reset-token-999", "BrandNewPassword123!");

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password has been reset successfully."));

        PasswordResetToken updatedToken = passwordResetTokenDao.findByToken("usable-reset-token-999").orElseThrow();
        assertThat(updatedToken.isUsed()).isTrue();

        // Login with new password succeeds
        LoginRequest loginSuccess = new LoginRequest("resetter@example.com", "BrandNewPassword123!");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginSuccess)))
                .andExpect(status().isOk());

        // Login with old password fails
        LoginRequest loginFail = new LoginRequest("resetter@example.com", "OldPassword123!");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginFail)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPasswordResetWhenTokenIsReused() throws Exception {
        User user = User.builder()
                .username("Reuser")
                .email("reuser@example.com")
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .token("already-used-token")
                .user(user)
                .expiryDate(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(true) // already used
                .build();
        passwordResetTokenDao.save(token);

        ResetPasswordRequest resetRequest = new ResetPasswordRequest("already-used-token", "NewPassword123!");

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Password reset token has already been used."));
    }

    @Test
    void rejectsPasswordResetWhenTokenIsExpired() throws Exception {
        User user = User.builder()
                .username("Expired User")
                .email("expired.user@example.com")
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .token("expired-token")
                .user(user)
                .expiryDate(Instant.now().minus(5, ChronoUnit.MINUTES)) // expired 5 mins ago
                .used(false)
                .build();
        passwordResetTokenDao.save(token);

        ResetPasswordRequest resetRequest = new ResetPasswordRequest("expired-token", "NewPassword123!");

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Password reset token has expired."));
    }

    @Test
    void rejectsPasswordResetWhenTokenDoesNotExist() throws Exception {
        ResetPasswordRequest resetRequest = new ResetPasswordRequest("non-existent-token", "NewPassword123!");

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Invalid password reset token."));
    }

    @Test
    void rejectsPasswordResetWhenPasswordIsTooShort() throws Exception {
        Map<String, String> request = Map.of(
                "token", "any-token",
                "newPassword", "123"
        );

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void verifiesUsedTokenReturnsValidFalse() throws Exception {
        User user = User.builder()
                .username("Used Token User")
                .email("usedtoken@example.com")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .token("used-verify-token")
                .user(user)
                .expiryDate(Instant.now().plus(30, ChronoUnit.MINUTES))
                .used(true)
                .build();
        passwordResetTokenDao.save(token);

        mvc.perform(get("/api/auth/verify-reset-token")
                        .param("token", "used-verify-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Password reset token has already been used."));
    }

    @Test
    void verifiesExpiredTokenReturnsValidFalse() throws Exception {
        User user = User.builder()
                .username("Expired Verify User")
                .email("expiredverify@example.com")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        PasswordResetToken token = PasswordResetToken.builder()
                .token("expired-verify-token")
                .user(user)
                .expiryDate(Instant.now().minus(10, ChronoUnit.MINUTES))
                .used(false)
                .build();
        passwordResetTokenDao.save(token);

        mvc.perform(get("/api/auth/verify-reset-token")
                        .param("token", "expired-verify-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Password reset token has expired."));
    }

    @Test
    void verifiesBlankTokenReturnsValidFalse() throws Exception {
        mvc.perform(get("/api/auth/verify-reset-token")
                        .param("token", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Invalid password reset token."));
    }

    @Test
    void rejectsPasswordResetWhenTokenIsBlank() throws Exception {
        Map<String, String> request = Map.of(
                "token", "",
                "newPassword", "ValidPassword123!"
        );

        mvc.perform(post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsForgotPasswordRequestWithBlankEmail() throws Exception {
        Map<String, String> badRequest = Map.of("email", "  ");

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsForgotPasswordRequestWithInvalidEmail() throws Exception {
        Map<String, String> badRequest = Map.of("email", "not-an-email");

        mvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }
}
