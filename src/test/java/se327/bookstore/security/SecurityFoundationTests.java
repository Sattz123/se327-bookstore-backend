package se327.bookstore.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import se327.bookstore.dao.UserDao;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SecurityFoundationTests.SecurityTestController.class)
class SecurityFoundationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private BookstoreUserDetailsService userDetailsService;

    @Autowired
    private UserDao userDao;

    @Test
    void passwordEncoderHashesAndMatchesPasswordsSafely() {
        String rawPassword = "P@ssword123!";
        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        assertThat(hash1)
                .isNotEqualTo(rawPassword)
                .isNotEqualTo(hash2); // Salt ensures distinct hashes
        assertThat(passwordEncoder.matches(rawPassword, hash1)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, hash2)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", hash1)).isFalse();
    }

    @Test
    void jwtTokenServiceGeneratesAndValidatesTokensWithClaims() {
        User user = User.builder()
                .id(999L)
                .username("TestUser")
                .email("testuser@example.com")
                .passwordHash("hashed")
                .role(Role.CUSTOMER)
                .build();

        String token = jwtTokenService.generateToken(user);

        assertThat(token).isNotBlank();
        assertThat(jwtTokenService.validateToken(token)).isTrue();
        assertThat(jwtTokenService.extractEmail(token)).isEqualTo("testuser@example.com");
        assertThat(jwtTokenService.extractUserId(token)).isEqualTo(999L);
        assertThat(jwtTokenService.extractRole(token)).isEqualTo("CUSTOMER");

        assertThat(jwtTokenService.validateToken("invalid.jwt.token")).isFalse();
        assertThat(jwtTokenService.validateToken(token + "tampered")).isFalse();
    }

    @Test
    void userDetailsServiceLoadsUserAndSetsGrantedAuthorities() {
        User user = User.builder()
                .username("AdminJane")
                .email("jane.admin@example.com")
                .passwordHash(passwordEncoder.encode("SecretPass"))
                .role(Role.ADMIN)
                .build();
        userDao.save(user);

        UserDetails details = userDetailsService.loadUserByUsername("JANE.ADMIN@example.com");
        assertThat(details.getUsername()).isEqualTo("jane.admin@example.com");
        assertThat(passwordEncoder.matches("SecretPass", details.getPassword())).isTrue();
        assertThat(details.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("nonexistent@example.com"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void permitsPublicCatalogAndOptionsPreflightWithoutToken() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk());

        mvc.perform(options("/api/products")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void rejectsUnauthenticatedAccessToSecuredEndpointWithProblemDetail() throws Exception {
        mvc.perform(get("/api/test-secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.instance").value("/api/test-secured"));
    }

    @Test
    void allowsSecuredEndpointWithValidCustomerToken() throws Exception {
        User customer = User.builder()
                .username("CustomerUser")
                .email("cust@example.com")
                .passwordHash(passwordEncoder.encode("Pass"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(customer);

        String token = jwtTokenService.generateToken(customer);

        mvc.perform(get("/api/test-secured")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("secured-ok"));
    }

    @Test
    void rejectsCustomerAccessToAdminEndpointWithProblemDetailForbidden() throws Exception {
        User customer = User.builder()
                .username("RegularCustomer")
                .email("regcust@example.com")
                .passwordHash(passwordEncoder.encode("Pass"))
                .role(Role.CUSTOMER)
                .build();
        userDao.save(customer);

        String customerToken = jwtTokenService.generateToken(customer);

        mvc.perform(get("/api/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.instance").value("/api/admin/test"));
    }

    @Test
    void allowsAdminAccessToAdminEndpointWithValidAdminToken() throws Exception {
        User admin = User.builder()
                .username("SuperAdmin")
                .email("superadmin@example.com")
                .passwordHash(passwordEncoder.encode("AdminPass"))
                .role(Role.ADMIN)
                .build();
        userDao.save(admin);

        String adminToken = jwtTokenService.generateToken(admin);

        mvc.perform(get("/api/admin/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string("admin-ok"));
    }

    @RestController
    static class SecurityTestController {
        @GetMapping("/api/test-secured")
        public String secured() {
            return "secured-ok";
        }

        @GetMapping("/api/admin/test")
        public String adminOnly() {
            return "admin-ok";
        }
    }
}
