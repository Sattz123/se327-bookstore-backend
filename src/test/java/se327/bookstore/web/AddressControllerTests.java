package se327.bookstore.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.entity.Address;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.repository.AddressRepository;
import se327.bookstore.repository.UserRepository;
import se327.bookstore.security.JwtTokenService;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=address_api_test",
        "spring.flyway.default-schema=address_api_test",
        "spring.flyway.schemas=address_api_test",
        "spring.jpa.properties.hibernate.default_schema=address_api_test"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class AddressControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private JwtTokenService jwtTokenService;

    private User customer;
    private String token;
    private static final String BODY = """
            {"label":" Home ","fullName":" Reader One ","phone":"0812345678",
             "addressLine":" 123 Main Street ","city":"Chiang Mai","province":"Chiang Mai",
             "postalCode":"50200","isDefault":true}
            """;

    @BeforeEach
    void prepareCustomer() {
        customer = userRepository.save(User.builder().username("Address reader").email("addresses@example.com")
                .passwordHash("test-password-hash").role(Role.CUSTOMER).build());
        token = "Bearer " + jwtTokenService.generateToken(customer);
    }

    @Test
    void requiresAuthenticationForEveryAddressOperation() throws Exception {
        mvc.perform(get("/api/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/addresses").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/addresses/1").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/addresses/1/default")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/addresses/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsAndListsTheCurrentCustomersAddress() throws Exception {
        mvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value("Home"))
                .andExpect(jsonPath("$.fullName").value("Reader One"))
                .andExpect(jsonPath("$.addressLine").value("123 Main Street"))
                .andExpect(jsonPath("$.isDefault").value(true))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.user").doesNotExist());
        mvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void updatesAndDeletesAnOwnedAddress() throws Exception {
        Address address = addressRepository.saveAndFlush(Address.builder().user(customer).label("Old")
                .fullName("Old recipient").addressLine("Old street").build());
        mvc.perform(put("/api/addresses/{id}", address.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Home"));
        mvc.perform(delete("/api/addresses/{id}", address.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }
}
