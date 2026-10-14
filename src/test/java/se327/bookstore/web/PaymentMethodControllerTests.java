package se327.bookstore.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.time.ZoneOffset;
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

    @Test void returnsAnEmptyListForANewCustomer() throws Exception {
        mvc.perform(get("/api/payment-methods").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void ownershipChecksDoNotExposeOrChangeAnotherCustomersRecords() throws Exception {
        User other = users.saveAndFlush(User.builder().username("Other reader").email("other.payments@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        PaymentMethod mine = saved(customer, true);
        PaymentMethod theirs = saved(other, true);
        mvc.perform(get("/api/payment-methods").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mine.getId()));
        for (long id : new long[]{theirs.getId(), Long.MAX_VALUE}) {
            mvc.perform(put("/api/payment-methods/{id}/default", id).header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Payment method not found."));
            mvc.perform(delete("/api/payment-methods/{id}", id).header("Authorization", token))
                    .andExpect(status().isNotFound());
        }
        assertThat(methods.findById(mine.getId()).orElseThrow().isDefaultMethod()).isTrue();
        assertThat(methods.findById(theirs.getId()).orElseThrow().isDefaultMethod()).isTrue();
    }

    @ParameterizedTest @CsvSource({"provider,blank", "cardholderName,blank", "provider,long",
            "cardholderName,long", "lastFour,123", "lastFour,12345", "lastFour,1234567890123456", "lastFour,abcd",
            "expiryMonth,0", "expiryMonth,13", "expiryYear,1999", "expiryYear,10000"})
    void rejectsInvalidFieldsWithoutReplacingTheDefault(String field, String value) throws Exception {
        PaymentMethod original = saved(customer, true);
        var values = payload();
        values.put(field, switch (value) {
            case "blank" -> "   ";
            case "long" -> "x".repeat(field.equals("provider") ? 51 : 101);
            default -> field.startsWith("expiry") ? Integer.valueOf(value) : value;
        });
        mvc.perform(post("/api/payment-methods").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(values)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value(field));
        assertThat(methods.findByUserIdOrderByDefaultMethodDescCreatedAtDescIdDesc(customer.getId()))
                .singleElement().extracting(PaymentMethod::isDefaultMethod).isEqualTo(true);
        assertThat(methods.existsById(original.getId())).isTrue();
    }

    @Test void rejectsMissingRequiredValues() throws Exception {
        mvc.perform(post("/api/payment-methods").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        assertThat(methods.findByUserIdOrderByDefaultMethodDescCreatedAtDescIdDesc(customer.getId())).isEmpty();
    }

    @Test void rejectsExpiredDatesButAcceptsTheCurrentMonth() throws Exception {
        PaymentMethod original = saved(customer, true);
        YearMonth now = YearMonth.now(ZoneOffset.UTC);
        var values = payload();
        values.put("expiryMonth", now.minusMonths(1).getMonthValue());
        values.put("expiryYear", now.minusMonths(1).getYear());
        mvc.perform(post("/api/payment-methods").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(values)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Expiry date must not be in the past."));
        assertThat(methods.findById(original.getId()).orElseThrow().isDefaultMethod()).isTrue();
        values.put("expiryMonth", now.getMonthValue());
        values.put("expiryYear", now.getYear());
        mvc.perform(post("/api/payment-methods").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(values))).andExpect(status().isCreated());
        assertThat(methods.findById(original.getId()).orElseThrow().isDefaultMethod()).isFalse();
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidIds(String id) throws Exception {
        mvc.perform(put("/api/payment-methods/{id}/default", id).header("Authorization", token))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/payment-methods/{id}", id).header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test void selectingADefaultIsRepeatableAndListsItFirst() throws Exception {
        PaymentMethod first = saved(customer, true);
        PaymentMethod second = saved(customer, false);
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/payment-methods/{id}/default", second.getId()).header("Authorization", token))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.isDefault").value(true));
        }
        assertThat(methods.findById(first.getId()).orElseThrow().isDefaultMethod()).isFalse();
        mvc.perform(get("/api/payment-methods").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(second.getId()))
                .andExpect(jsonPath("$[1].isDefault").value(false));
    }

    @Test void addingANonDefaultPreservesTheExistingDefault() throws Exception {
        PaymentMethod original = saved(customer, true);
        var values = payload();
        values.put("isDefault", false);
        mvc.perform(post("/api/payment-methods").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(values)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.isDefault").value(false));
        assertThat(methods.findById(original.getId()).orElseThrow().isDefaultMethod()).isTrue();
    }

    @Test void deletingANonDefaultPreservesTheDefaultAndDeletingTheDefaultDoesNotPromoteAnother() throws Exception {
        PaymentMethod original = saved(customer, true);
        PaymentMethod spare = saved(customer, false);
        PaymentMethod remaining = saved(customer, false);
        mvc.perform(delete("/api/payment-methods/{id}", spare.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());
        assertThat(methods.findById(original.getId()).orElseThrow().isDefaultMethod()).isTrue();
        mvc.perform(delete("/api/payment-methods/{id}", original.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/payment-methods").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(remaining.getId())).andExpect(jsonPath("$[0].isDefault").value(false));
    }

    private PaymentMethod saved(User owner, boolean isDefault) {
        return methods.saveAndFlush(PaymentMethod.builder().user(owner).provider("Visa").cardholderName("Demo Reader")
                .lastFour("1234").expiryMonth(12).expiryYear(YearMonth.now().plusYears(2).getYear())
                .defaultMethod(isDefault).build());
    }

    private Map<String, Object> payload() {
        return new HashMap<>(Map.of("provider", " Visa ", "cardholderName", " Demo Reader ", "lastFour", "1234",
                "expiryMonth", 12, "expiryYear", YearMonth.now().plusYears(2).getYear(), "isDefault", true));
    }
}
