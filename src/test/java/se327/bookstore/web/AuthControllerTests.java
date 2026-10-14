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
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.dto.RegisterRequest;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.security.JwtTokenService;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserDao userDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Test
    void registersCustomerSuccessfullyAndReturnsTokenAndUserDto() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Alice Smith",
                "alice.smith@example.com",
                "Secret123!",
                "0812345678"
        );

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.id").isNumber())
                .andExpect(jsonPath("$.user.username").value("Alice Smith"))
                .andExpect(jsonPath("$.user.email").value("alice.smith@example.com"))
                .andExpect(jsonPath("$.user.phone").value("0812345678"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.createdAt").isNotEmpty());

        assertThat(userDao.existsByEmail("alice.smith@example.com")).isTrue();
    }

    @Test
    void rejectsRegistrationWhenFieldsAreMissingOrInvalid() throws Exception {
        Map<String, String> invalidPayload = Map.of(
                "username", " ",
                "email", "not-an-email",
                "password", "123"
        );

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void rejectsRegistrationWithDuplicateEmailRegardlessOfCase() throws Exception {
        RegisterRequest first = new RegisterRequest("Bob Original", "bob@example.com", "Password123!", null);
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        RegisterRequest duplicate = new RegisterRequest("Bob Copy", "BOB@EXAMPLE.COM", "AnotherPass123!", null);
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Email Already Registered"))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    @Test
    void publicRegistrationAlwaysCreatesCustomerRoleEvenIfJsonContainsRoleAdmin() throws Exception {
        Map<String, Object> payloadWithRole = Map.of(
                "username", "Hacker",
                "email", "hacker@example.com",
                "password", "HackedPass123!",
                "role", "ADMIN"
        );

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payloadWithRole)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        User saved = userDao.findByEmail("hacker@example.com").orElseThrow();
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void authenticatesValidCredentialsAndReturnsTokenAndUserDto() throws Exception {
        User user = User.builder()
                .username("Charlie Brown")
                .email("charlie@example.com")
                .passwordHash(passwordEncoder.encode("GoodGrief123!"))
                .phone("0899998888")
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        LoginRequest loginRequest = new LoginRequest("CHARLIE@EXAMPLE.COM", "GoodGrief123!");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value("charlie@example.com"))
                .andExpect(jsonPath("$.user.username").value("Charlie Brown"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }

    @Test
    void rejectsLoginWithWrongPasswordWithProblemDetail() throws Exception {
        User user = User.builder()
                .username("Dana")
                .email("dana@example.com")
                .passwordHash(passwordEncoder.encode("CorrectPassword123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        LoginRequest loginRequest = new LoginRequest("dana@example.com", "WrongPassword!");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void rejectsLoginWithNonexistentEmailWithProblemDetail() throws Exception {
        LoginRequest loginRequest = new LoginRequest("ghost@example.com", "SomePassword123!");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void returnsCurrentUserForAuthenticatedUser() throws Exception {
        User user = User.builder()
                .username("Emma Watson")
                .email("emma@example.com")
                .passwordHash(passwordEncoder.encode("Hermione123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        String token = jwtTokenService.generateToken(user);

        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("emma@example.com"))
                .andExpect(jsonPath("$.username").value("Emma Watson"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void rejectsCurrentUserWhenUnauthenticated() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void logsOutSuccessfully() throws Exception {
        mvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully."));
    }
}
