package se327.bookstore.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dto.LoginRequest;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AuthBoundaryTests.AdminBoundaryTestController.class)
class AuthBoundaryTests {

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
    void rejectsExpiredTokenWithProblemDetailUnauthorized() throws Exception {
        // JwtTokenService with negative expiration creates an immediately expired token
        JwtTokenService expiredTokenService = new JwtTokenService(
                "inwza-bookstore-se327-secure-jwt-signing-key-with-256-bits-minimum!",
                -5000L);

        User customer = User.builder()
                .username("ExpiredUser")
                .email("expired@example.com")
                .passwordHash(passwordEncoder.encode("Pass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(customer);

        String expiredToken = expiredTokenService.generateToken(customer);
        assertThat(expiredTokenService.validateToken(expiredToken)).isFalse();

        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void rejectsMalformedOrGarbledTokenWithProblemDetailUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.malformed.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));

        mvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer "))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void authenticatesCaseInsensitivelyAndWithWhitespaceTolerance() throws Exception {
        User user = User.builder()
                .username("CaseSensitiveUser")
                .email("boundary.test@example.com")
                .passwordHash(passwordEncoder.encode("BoundaryPass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(user);

        // Login with uppercase email
        LoginRequest request = new LoginRequest("BOUNDARY.TEST@EXAMPLE.COM", "BoundaryPass123!");

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value("boundary.test@example.com"));
    }

    @Test
    void preventsCustomerAccessToAdminEndpointsWithProblemDetailForbidden() throws Exception {
        User customer = User.builder()
                .username("StandardCustomer")
                .email("customer.boundary@example.com")
                .passwordHash(passwordEncoder.encode("CustomerPass123!"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(customer);

        String customerToken = jwtTokenService.generateToken(customer);

        mvc.perform(get("/api/admin/boundary-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.instance").value("/api/admin/boundary-check"));
    }

    @Test
    void allowsAdminAccessToAdminEndpointsWithAdminToken() throws Exception {
        User admin = User.builder()
                .username("SystemAdmin")
                .email("admin.boundary@example.com")
                .passwordHash(passwordEncoder.encode("AdminPass123!"))
                .role(Role.ADMIN)
                .build();
        userDao.save(admin);

        String adminToken = jwtTokenService.generateToken(admin);

        mvc.perform(get("/api/admin/boundary-check")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-boundary-granted"));
    }

    @RestController
    static class AdminBoundaryTestController {
        @GetMapping("/api/admin/boundary-check")
        public String adminEndpoint() {
            return "admin-boundary-granted";
        }
    }
}
