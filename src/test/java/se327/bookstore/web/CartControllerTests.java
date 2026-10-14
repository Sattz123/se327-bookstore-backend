package se327.bookstore.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

    @Test void emptyCartHasZeroTotals() throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalQuantity").value(0))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test void duplicateAddsMergeOnlyTheSameFormatAndAnUpdateReplacesItsQuantity() throws Exception {
        add(2, "PAPERBACK", 201);
        add(3, "PAPERBACK", 201);
        add(1, "HARDCOVER", 201);
        var saved = items.findByUserIdOrderByIdAsc(customer.getId());
        assertThat(saved).hasSize(2);
        assertThat(saved.getFirst().getQuantity()).isEqualTo(5);
        mvc.perform(put("/api/cart/items/{id}", saved.getFirst().getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].format").value("PAPERBACK"))
                .andExpect(jsonPath("$.items[1].format").value("HARDCOVER"))
                .andExpect(jsonPath("$.totalQuantity").value(3)).andExpect(jsonPath("$.total").value(59.85));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "null", "1.5", "2147483648"})
    void rejectsInvalidQuantitiesOnAddAndUpdate(String quantity) throws Exception {
        CartItem original = saved(customer, BookFormat.PAPERBACK, 2);
        String body = "{\"productId\":" + book.getId() + ",\"format\":\"PAPERBACK\",\"quantity\":" + quantity + "}";
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/cart/items/{id}", original.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":" + quantity + "}"))
                .andExpect(status().isBadRequest());
        assertThat(items.findById(original.getId()).orElseThrow().getQuantity()).isEqualTo(2);
    }

    @Test void rejectsMissingFieldsAndUnsupportedFormats() throws Exception {
        for (String body : new String[]{"{}", payload(1, "EBOOK"), payload(1, "UNKNOWN")}) {
            mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        assertThat(items.findByUserIdOrderByIdAsc(customer.getId())).isEmpty();
    }

    @Test void stockLimitsIncludeDuplicateAddsAndDifferentFormatsWithoutChangingTheCartOnFailure() throws Exception {
        add(6, "PAPERBACK", 201);
        add(5, "PAPERBACK", 409);
        add(5, "HARDCOVER", 409);
        add(4, "HARDCOVER", 201);
        CartItem paperback = items.findByUserIdOrderByIdAsc(customer.getId()).getFirst();
        mvc.perform(put("/api/cart/items/{id}", paperback.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":7}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/cart").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalQuantity").value(10))
                .andExpect(jsonPath("$.total").value(199.50));
        assertThat(items.findById(paperback.getId()).orElseThrow().getQuantity()).isEqualTo(6);
    }

    @Test void addingCannotOverflowAnExistingQuantity() throws Exception {
        saved(customer, BookFormat.PAPERBACK, 1);
        add(Integer.MAX_VALUE, "PAPERBACK", 409);
        assertThat(items.findByUserIdOrderByIdAsc(customer.getId())).singleElement().extracting(CartItem::getQuantity).isEqualTo(1);
    }

    @Test void preventsAddingMissingHiddenOrOutOfStockBooks() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":9223372036854775807,\"format\":\"PAPERBACK\",\"quantity\":1}"))
                .andExpect(status().isNotFound());
        book.setVisible(false); products.saveAndFlush(book);
        add(1, "PAPERBACK", 404);
        book.setVisible(true); book.setStockQuantity(0); products.saveAndFlush(book);
        add(1, "PAPERBACK", 409);
        book.setStockQuantity(10); book.getCategory().setVisible(false); categories.saveAndFlush(book.getCategory());
        add(1, "PAPERBACK", 404);
    }

    @Test void listUsesCurrentPricesAndMarksStaleSelectionsUnavailableButStillRemovable() throws Exception {
        CartItem original = saved(customer, BookFormat.PAPERBACK, 3);
        book.setPrice(new BigDecimal("20.10")); book.setStockQuantity(2); products.saveAndFlush(book);
        mvc.perform(get("/api/cart").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(60.30)).andExpect(jsonPath("$.items[0].available").value(false));
        mvc.perform(put("/api/cart/items/{id}", original.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].available").value(true));
        book.setVisible(false); products.saveAndFlush(book);
        mvc.perform(get("/api/cart").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].available").value(false));
        mvc.perform(delete("/api/cart/items/{id}", original.getId()).header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.total").value(0));
    }

    @Test void removedFormatsCannotBeUpdatedButCanBeRemoved() throws Exception {
        CartItem original = saved(customer, BookFormat.PAPERBACK, 1);
        book.getFormats().remove(BookFormat.PAPERBACK); products.saveAndFlush(book);
        mvc.perform(get("/api/cart").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].available").value(false));
        mvc.perform(put("/api/cart/items/{id}", original.getId()).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/cart/items/{id}", original.getId()).header("Authorization", token)).andExpect(status().isOk());
    }

    @Test void ownershipIsEnforcedForReadsUpdatesAndRemoval() throws Exception {
        User other = users.saveAndFlush(User.builder().username("Other cart").email("other.cart@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        CartItem mine = saved(customer, BookFormat.PAPERBACK, 1);
        CartItem theirs = saved(other, BookFormat.PAPERBACK, 4);
        mvc.perform(get("/api/cart").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(mine.getId()));
        for (long id : new long[]{theirs.getId(), Long.MAX_VALUE}) {
            mvc.perform(put("/api/cart/items/{id}", id).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                    .andExpect(status().isNotFound());
            mvc.perform(delete("/api/cart/items/{id}", id).header("Authorization", token))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Cart item not found."));
        }
        assertThat(items.findById(theirs.getId()).orElseThrow().getQuantity()).isEqualTo(4);
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "abc"})
    void rejectsInvalidItemIds(String id) throws Exception {
        mvc.perform(put("/api/cart/items/{id}", id).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/cart/items/{id}", id).header("Authorization", token)).andExpect(status().isBadRequest());
    }

    @Test void removalPreservesOtherFormatsAndReachesAnEmptyCart() throws Exception {
        CartItem first = saved(customer, BookFormat.PAPERBACK, 2);
        CartItem second = saved(customer, BookFormat.HARDCOVER, 1);
        mvc.perform(delete("/api/cart/items/{id}", first.getId()).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].format").value("HARDCOVER")).andExpect(jsonPath("$.total").value(19.95));
        mvc.perform(delete("/api/cart/items/{id}", second.getId()).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalQuantity").value(0));
    }

    private CartItem saved(User owner, BookFormat format, int quantity) {
        return items.saveAndFlush(CartItem.builder().user(owner).product(book).format(format).quantity(quantity).build());
    }
    private void add(int quantity, String format, int expectedStatus) throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(payload(quantity, format))).andExpect(status().is(expectedStatus));
    }

    private String payload(int quantity, String format) throws Exception {
        return mapper.writeValueAsString(Map.of("productId", book.getId(), "quantity", quantity, "format", format));
    }
}
