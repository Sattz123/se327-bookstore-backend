package se327.bookstore.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.entity.*;
import se327.bookstore.repository.*;
import se327.bookstore.security.JwtTokenService;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=payment_api_test", "spring.flyway.default-schema=payment_api_test",
        "spring.flyway.schemas=payment_api_test", "spring.jpa.properties.hibernate.default_schema=payment_api_test"
})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class PaymentMethodControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private PaymentMethodRepository methods;
    @Autowired private JwtTokenService jwt;
    private User customer;
    private String token;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        customer = users.saveAndFlush(User.builder().username("Payment reader").email("payments@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        token = "Bearer " + jwt.generateToken(customer);
    }

    @Test void requiresAuthenticationForAllOperations() throws Exception {
        mvc.perform(get("/api/payment-methods")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/payment-methods").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(payload())))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/payment-methods/1/default")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/payment-methods/1")).andExpect(status().isUnauthorized());
    }

    @Test void savesOnlyDisplayMetadataAndReturnsAMaskedNumber() throws Exception {
        mvc.perform(post("/api/payment-methods").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(payload())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.provider").value("Visa"))
                .andExpect(jsonPath("$.cardholderName").value("Demo Reader"))
                .andExpect(jsonPath("$.cardNumberMasked").value("**** **** **** 1234"))
                .andExpect(jsonPath("$.isDefault").value(true)).andExpect(jsonPath("$.user").doesNotExist());
        assertThat(methods.findByUserIdOrderByDefaultMethodDescCreatedAtDescIdDesc(customer.getId()))
                .singleElement().extracting(PaymentMethod::getLastFour).isEqualTo("1234");
        mvc.perform(get("/api/payment-methods").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardNumberMasked").value("**** **** **** 1234"));
    }

    private Map<String, Object> payload() {
        return new HashMap<>(Map.of("provider", " Visa ", "cardholderName", " Demo Reader ", "lastFour", "1234",
                "expiryMonth", 12, "expiryYear", YearMonth.now().plusYears(2).getYear(), "isDefault", true));
    }
}
