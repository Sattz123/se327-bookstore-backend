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
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=checkout_api_test", "spring.flyway.default-schema=checkout_api_test",
        "spring.flyway.schemas=checkout_api_test", "spring.jpa.properties.hibernate.default_schema=checkout_api_test"
})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CheckoutControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private CartItemRepository carts;
    @Autowired private AddressRepository addresses;
    @Autowired private PaymentMethodRepository payments;
    @Autowired private OrderRepository orders;
    @Autowired private JwtTokenService jwt;
    private User customer;
    private Product book;
    private Address address;
    private String token;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        customer = users.saveAndFlush(User.builder().username("Checkout reader").email("checkout@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        token = "Bearer " + jwt.generateToken(customer);
        Category category = categories.saveAndFlush(Category.builder().name("Checkout books").build());
        book = products.saveAndFlush(Product.builder().category(category).name("Purchased book").price(new BigDecimal("19.95"))
                .stockQuantity(10).formats(new HashSet<>(Set.of(BookFormat.PAPERBACK, BookFormat.HARDCOVER))).build());
        carts.saveAndFlush(CartItem.builder().user(customer).product(book).format(BookFormat.PAPERBACK).quantity(2).build());
        address = addresses.saveAndFlush(Address.builder().user(customer).label("Home").fullName("Demo Reader")
                .addressLine("123 Test Street").city("Chiang Mai").postalCode("50200").build());
    }

    @Test void checkoutEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/checkout/review")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/checkout").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/checkout/orders/1")).andExpect(status().isUnauthorized());
    }

    @Test void createsAPendingCashOrderAndReturnsAPrivateConfirmation() throws Exception {
        var request = request();
        String result = mvc.perform(post("/api/checkout").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(39.90)).andExpect(jsonPath("$.items[0].title").value("Purchased book"))
                .andExpect(jsonPath("$.items[0].format").value("PAPERBACK")).andExpect(jsonPath("$.recipientName").value("Demo Reader"))
                .andExpect(jsonPath("$.user").doesNotExist()).andReturn().getResponse().getContentAsString();
        Long id = mapper.readTree(result).get("id").asLong();
        mvc.perform(get("/api/checkout/orders/{id}", id).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.paymentDisplay").value("Cash on delivery"));
        assertThat(carts.findByUserIdOrderByIdAsc(customer.getId())).isEmpty();
        assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
    }

    private Map<String, Object> request() throws Exception {
        String review = mvc.perform(get("/api/checkout/review").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new HashMap<>(Map.of("addressId", address.getId(), "paymentType", "CASH_ON_DELIVERY",
                "checkoutKey", UUID.randomUUID().toString(), "cartFingerprint", mapper.readTree(review).get("cartFingerprint").asText()));
    }
}
