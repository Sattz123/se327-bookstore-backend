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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=cart_api_test", "spring.flyway.default-schema=cart_api_test",
        "spring.flyway.schemas=cart_api_test", "spring.jpa.properties.hibernate.default_schema=cart_api_test"
})
@ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class CartControllerTests {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private CategoryRepository categories;
    @Autowired private ProductRepository products;
    @Autowired private CartItemRepository items;
    @Autowired private JwtTokenService jwt;
    private User customer;
    private Product book;
    private String token;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach void setup() {
        customer = users.saveAndFlush(User.builder().username("Cart reader").email("cart@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        token = "Bearer " + jwt.generateToken(customer);
        Category category = categories.saveAndFlush(Category.builder().name("Cart books").build());
        book = products.saveAndFlush(Product.builder().category(category).name("Cart book").price(new BigDecimal("19.95"))
                .stockQuantity(10).formats(new HashSet<>(Set.of(BookFormat.PAPERBACK, BookFormat.HARDCOVER))).build());
    }

    @Test void allCartEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cart/items").contentType(MediaType.APPLICATION_JSON).content(payload(2, "PAPERBACK")))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/cart/items/1").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/cart/items/1")).andExpect(status().isUnauthorized());
    }

    @Test void persistsTheSelectedFormatAndCalculatesTotalsFromTheCatalogPrice() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(payload(2, "PAPERBACK")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.items[0].format").value("PAPERBACK"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(19.95)).andExpect(jsonPath("$.items[0].lineTotal").value(39.90))
                .andExpect(jsonPath("$.total").value(39.90)).andExpect(jsonPath("$.totalQuantity").value(2));
        mvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].format").value("PAPERBACK"))
                .andExpect(jsonPath("$.total").value(39.90));
        assertThat(items.findByUserIdOrderByIdAsc(customer.getId())).singleElement()
                .extracting(CartItem::getQuantity).isEqualTo(2);
        assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    private String payload(int quantity, String format) throws Exception {
        return mapper.writeValueAsString(Map.of("productId", book.getId(), "quantity", quantity, "format", format));
    }
}
