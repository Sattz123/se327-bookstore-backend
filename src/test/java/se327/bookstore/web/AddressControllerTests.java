package se327.bookstore.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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

    @ParameterizedTest
    @ValueSource(strings = {"label", "fullName", "addressLine"})
    void rejectsBlankRequiredFields(String field) throws Exception {
        var values = validFields();
        values.put(field, "   ");
        mvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(values)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(customer.getId())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"label", "fullName", "phone", "addressLine", "city", "province", "postalCode"})
    void rejectsOverlongFieldsWithoutChangingAnExistingDefault(String field) throws Exception {
        Address original = savedAddress(customer, "Original", true);
        Map<String, Integer> limits = Map.of("label", 50, "fullName", 200, "phone", 20,
                "addressLine", 500, "city", 100, "province", 100, "postalCode", 10);
        var values = validFields();
        values.put(field, "x".repeat(limits.get(field) + 1));
        mvc.perform(put("/api/addresses/{id}", original.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(new ObjectMapper().writeValueAsString(values)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(addressRepository.findById(original.getId()).orElseThrow().isDefaultAddress()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidAddressIds(String id) throws Exception {
        mvc.perform(put("/api/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/addresses/{id}/default", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotReadOrModifyAnotherCustomersAddressesOrClearTheirDefaults() throws Exception {
        User other = userRepository.save(User.builder().username("Other reader").email("other.address@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        Address mine = savedAddress(customer, "Mine", true);
        Address theirs = savedAddress(other, "Private", true);
        mvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()));
        for (Long id : new Long[]{theirs.getId(), Long.MAX_VALUE}) {
            mvc.perform(put("/api/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isNotFound());
            mvc.perform(put("/api/addresses/{id}/default", id).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Address not found."));
            mvc.perform(delete("/api/addresses/{id}", id).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isNotFound());
        }
        assertThat(addressRepository.findById(mine.getId()).orElseThrow().isDefaultAddress()).isTrue();
        assertThat(addressRepository.findById(theirs.getId()).orElseThrow().getLabel()).isEqualTo("Private");
        assertThat(addressRepository.findById(theirs.getId()).orElseThrow().isDefaultAddress()).isTrue();
    }

    @Test
    void switchesDefaultsAndAllowsSelectingTheSameDefaultAgain() throws Exception {
        Address first = savedAddress(customer, "Home", true);
        Address second = savedAddress(customer, "Work", false);
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/addresses/{id}/default", second.getId()).header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(true));
        }
        assertThat(addressRepository.findById(first.getId()).orElseThrow().isDefaultAddress()).isFalse();
        mvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].isDefault").value(false));
    }

    @Test
    void addingAndEditingDefaultsReplacesThePreviousDefault() throws Exception {
        Address original = savedAddress(customer, "Old default", true);
        mvc.perform(post("/api/addresses").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());
        assertThat(addressRepository.findById(original.getId()).orElseThrow().isDefaultAddress()).isFalse();
        mvc.perform(put("/api/addresses/{id}", original.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isOk());
        assertThat(addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(customer.getId()))
                .filteredOn(Address::isDefaultAddress).hasSize(1)
                .extracting(Address::getId).containsExactly(original.getId());
    }

    @Test
    void deletingTheDefaultLeavesOtherAddressesWithoutPromotingThem() throws Exception {
        Address original = savedAddress(customer, "Default", true);
        Address remaining = savedAddress(customer, "Other", false);
        mvc.perform(delete("/api/addresses/{id}", original.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/addresses").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(remaining.getId()))
                .andExpect(jsonPath("$[0].isDefault").value(false));
    }

    @Test
    void deletingANonDefaultDoesNotChangeTheDefault() throws Exception {
        Address original = savedAddress(customer, "Default", true);
        Address other = savedAddress(customer, "Other", false);
        mvc.perform(delete("/api/addresses/{id}", other.getId()).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNoContent());
        assertThat(addressRepository.findById(original.getId()).orElseThrow().isDefaultAddress()).isTrue();
    }

    @Test
    void canClearTheDefaultAndNormalizeOptionalBlankFields() throws Exception {
        Address original = savedAddress(customer, "Default", true);
        mvc.perform(put("/api/addresses/{id}", original.getId()).header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                            {"label":"Home","fullName":"Reader","addressLine":"Street",
                             "phone":"  ","city":"  ","province":"  ","postalCode":"  ","isDefault":false}
                            """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(false))
                .andExpect(jsonPath("$.phone").isEmpty()).andExpect(jsonPath("$.city").isEmpty())
                .andExpect(jsonPath("$.province").isEmpty()).andExpect(jsonPath("$.postalCode").isEmpty());
    }

    private Address savedAddress(User owner, String label, boolean isDefault) {
        return addressRepository.saveAndFlush(Address.builder().user(owner).label(label)
                .fullName("Recipient").addressLine("Test street").defaultAddress(isDefault).build());
    }

    private Map<String, Object> validFields() {
        return new HashMap<>(Map.of("label", "Home", "fullName", "Recipient", "addressLine", "Street", "isDefault", true));
    }

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
