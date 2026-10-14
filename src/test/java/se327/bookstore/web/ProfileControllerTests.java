package se327.bookstore.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.ChangePasswordRequest;
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.dto.UpdateProfileRequest;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.security.JwtTokenService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsCurrentCustomerProfileWithToken() throws Exception {
        User user = User.builder()
                .username("John Profile")
                .email("john.profile@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .phone("0811112222")
                .avatarUrl("https://example.com/avatar.jpg")
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        mvc.perform(get("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("John Profile"))
                .andExpect(jsonPath("$.email").value("john.profile@example.com"))
                .andExpect(jsonPath("$.phone").value("0811112222"))
                .andExpect(jsonPath("$.avatarUrl").value("https://example.com/avatar.jpg"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void rejectsReadingProfileWithoutToken() throws Exception {
        mvc.perform(get("/api/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void updatesProfileSuccessfullyAndReturnsNewToken() throws Exception {
        User user = User.builder()
                .username("Original Name")
                .email("original@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .phone("0800000000")
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                "Updated Name",
                "original@example.com",
                "0899999999",
                "https://example.com/new-avatar.png"
        );

        mvc.perform(put("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("Updated Name"))
                .andExpect(jsonPath("$.user.phone").value("0899999999"))
                .andExpect(jsonPath("$.user.avatarUrl").value("https://example.com/new-avatar.png"));

        User updated = userDao.findByEmail("original@example.com").orElseThrow();
        assertThat(updated.getUsername()).isEqualTo("Updated Name");
        assertThat(updated.getPhone()).isEqualTo("0899999999");
        assertThat(updated.getAvatarUrl()).isEqualTo("https://example.com/new-avatar.png");
    }

    @Test
    void updatesProfileEmailWhenAvailable() throws Exception {
        User user = User.builder()
                .username("Email Updater")
                .email("old.email@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        UpdateProfileRequest updateRequest = new UpdateProfileRequest(
                "Email Updater",
                "brand.new.email@example.com",
                null,
                null
        );

        mvc.perform(put("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("brand.new.email@example.com"));

        assertThat(userDao.findByEmail("brand.new.email@example.com")).isPresent();
        assertThat(userDao.findByEmail("old.email@example.com")).isEmpty();
    }

    @Test
    void rejectsProfileUpdateWhenEmailBelongsToAnotherUser() throws Exception {
        User userA = User.builder()
                .username("User A")
                .email("usera@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(userA);

        User userB = User.builder()
                .username("User B")
                .email("userb@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(userB);

        String tokenA = jwtTokenService.generateToken(userA);

        UpdateProfileRequest attemptDuplicate = new UpdateProfileRequest(
                "User A Renamed",
                "userb@example.com", // collision with User B
                null,
                null
        );

        mvc.perform(put("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attemptDuplicate)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email Already Registered"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));

        // Verify User A's email unchanged
        assertThat(userDao.findByEmail("usera@example.com")).isPresent();
    }

    @Test
    void changesPasswordSuccessfullyWhenCurrentPasswordIsCorrect() throws Exception {
        User user = User.builder()
                .username("Password Changer")
                .email("pwchange@example.com")
                .passwordHash(passwordEncoder.encode("OldPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "OldPassword123!",
                "NewPassword123!"
        );

        mvc.perform(put("/api/profile/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully."));

        // Verify login works with new password
        LoginRequest loginWithNew = new LoginRequest("pwchange@example.com", "NewPassword123!");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithNew)))
                .andExpect(status().isOk());

        // Verify old password no longer works
        LoginRequest loginWithOld = new LoginRequest("pwchange@example.com", "OldPassword123!");
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithOld)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordIsIncorrect() throws Exception {
        User user = User.builder()
                .username("Wrong Password User")
                .email("wrongpw@example.com")
                .passwordHash(passwordEncoder.encode("RealPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "IncorrectPassword!",
                "NewPassword123!"
        );

        mvc.perform(put("/api/profile/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Incorrect current password."));
    }

    @Test
    void rejectsPasswordChangeWhenNewPasswordIsSameAsCurrent() throws Exception {
        User user = User.builder()
                .username("Same Password User")
                .email("samepw@example.com")
                .passwordHash(passwordEncoder.encode("SamePassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        ChangePasswordRequest request = new ChangePasswordRequest(
                "SamePassword123!",
                "SamePassword123!"
        );

        mvc.perform(put("/api/profile/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value(containsString("same as the current password")));
    }

    @Test
    void rejectsPasswordChangeWhenNewPasswordIsTooShort() throws Exception {
        User user = User.builder()
                .username("Short PW User")
                .email("shortpw@example.com")
                .passwordHash(passwordEncoder.encode("CurrentPass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        Map<String, String> shortRequest = Map.of(
                "currentPassword", "CurrentPass123!",
                "newPassword", "123"
        );

        mvc.perform(put("/api/profile/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shortRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }
    @Test
    void rejectsUpdatingProfileWithoutToken() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "new@example.com", null, null);
        mvc.perform(put("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsChangingPasswordWithoutToken() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Current123!", "NewPass123!");
        mvc.perform(put("/api/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsProfileUpdateWithBlankUsernameOrInvalidEmail() throws Exception {
        User user = User.builder()
                .username("Valid User")
                .email("valid@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        UpdateProfileRequest badRequest = new UpdateProfileRequest("", "not-an-email", null, null);

        mvc.perform(put("/api/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordIsBlank() throws Exception {
        User user = User.builder()
                .username("Blank Current User")
                .email("blankcurrent@example.com")
                .passwordHash(passwordEncoder.encode("CurrentPass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        Map<String, String> request = Map.of(
                "currentPassword", "",
                "newPassword", "ValidNewPass123!"
        );

        mvc.perform(put("/api/profile/password")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }
}
