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
import java.time.YearMonth;
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

    @Test void simulatedCardCompletesTheOrderAndSnapshotsMaskedPaymentAndDelivery() throws Exception {
        PaymentMethod card = card(customer, YearMonth.now().plusYears(2));
        var body = request();
        body.put("paymentType", "SIMULATED_CARD"); body.put("paymentMethodId", card.getId());
        Long id = submit(body, 201);
        PurchaseOrder order = orders.findById(id).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getPaymentDisplay()).isEqualTo("Visa **** **** **** 1234");
        book.setName("New catalog title"); book.setPrice(new BigDecimal("25.00")); products.saveAndFlush(book);
        addresses.delete(address); addresses.flush(); payments.delete(card); payments.flush();
        mvc.perform(get("/api/checkout/orders/{id}", id).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Purchased book"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(19.95)).andExpect(jsonPath("$.total").value(39.90))
                .andExpect(jsonPath("$.shippingAddress").value("123 Test Street\nChiang Mai\n50200"))
                .andExpect(jsonPath("$.paymentDisplay").value("Visa **** **** **** 1234"));
    }

    @Test void identicalRetriesReturnOneOrderAndDoNotDeductTwiceOrClearANewerCart() throws Exception {
        var body = request();
        Long first = submit(body, 201);
        carts.saveAndFlush(CartItem.builder().user(customer).product(book).format(BookFormat.HARDCOVER).quantity(1).build());
        assertThat(submit(body, 201)).isEqualTo(first);
        assertThat(orders.count()).isEqualTo(1);
        assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
        assertThat(carts.findByUserIdOrderByIdAsc(customer.getId())).singleElement().extracting(CartItem::getQuantity).isEqualTo(1);
        body.put("paymentType", "SIMULATED_CARD");
        submit(body, 409);
        assertThat(orders.count()).isEqualTo(1);
    }

    @Test void rejectsEmptyCartWithoutCreatingAnOrder() throws Exception {
        var body = request();
        carts.deleteAll(); carts.flush();
        submit(body, 409);
        assertThat(orders.count()).isZero();
        assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
    }

    @Test void stalePricesAndChangedQuantitiesRequireAFreshReview() throws Exception {
        var body = request();
        book.setPrice(new BigDecimal("20.00")); products.saveAndFlush(book);
        submit(body, 409);
        assertUnchanged(10, 2);
        body = request();
        CartItem item = carts.findByUserIdOrderByIdAsc(customer.getId()).getFirst();
        item.setQuantity(3); carts.saveAndFlush(item);
        submit(body, 409);
        assertUnchanged(10, 3);
        mvc.perform(post("/api/checkout").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(60.00));
    }

    @Test void insufficientStockAcrossFormatsLeavesTheWholeCartAndInventoryUntouched() throws Exception {
        carts.saveAndFlush(CartItem.builder().user(customer).product(book).format(BookFormat.HARDCOVER).quantity(3).build());
        var body = request();
        book.setStockQuantity(4); products.saveAndFlush(book);
        submit(body, 409);
        assertThat(orders.count()).isZero();
        assertThat(carts.findByUserIdOrderByIdAsc(customer.getId())).hasSize(2);
        assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(4);
    }

    @Test void selectedFormatsAndHiddenBooksAreRechecked() throws Exception {
        var body = request();
        book.getFormats().remove(BookFormat.PAPERBACK); products.saveAndFlush(book);
        submit(body, 409);
        book.getFormats().add(BookFormat.PAPERBACK); book.setVisible(false); products.saveAndFlush(book);
        submit(body, 409);
        assertUnchanged(10, 2);
    }

    @Test void foreignOrMissingAddressAndPaymentIdsCannotPurchase() throws Exception {
        User other = users.saveAndFlush(User.builder().username("Other checkout").email("other.checkout@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        Address foreign = addresses.saveAndFlush(Address.builder().user(other).label("Private").fullName("Other Reader")
                .addressLine("Private Street").build());
        var body = request();
        for (Long id : new Long[]{foreign.getId(), Long.MAX_VALUE}) {
            body.put("addressId", id); submit(body, 404);
        }
        body.put("addressId", address.getId()); body.put("paymentType", "SIMULATED_CARD");
        PaymentMethod foreignCard = card(other, YearMonth.now().plusYears(2));
        for (Long id : new Long[]{foreignCard.getId(), Long.MAX_VALUE}) {
            body.put("paymentMethodId", id); submit(body, 404);
        }
        assertUnchanged(10, 2);
    }

    @Test void requiresAMatchingPaymentOptionAndRejectsExpiredCards() throws Exception {
        var body = request();
        body.put("paymentMethodId", card(customer, YearMonth.now().plusYears(2)).getId());
        submit(body, 400);
        body.put("paymentType", "SIMULATED_CARD"); body.remove("paymentMethodId"); submit(body, 400);
        body.put("paymentMethodId", card(customer, YearMonth.now().minusMonths(1)).getId()); submit(body, 400);
        assertUnchanged(10, 2);
    }

    @Test void confirmationIsOwnerOnlyAndRequestValidationRejectsMalformedKeys() throws Exception {
        Long id = submit(request(), 201);
        User other = users.saveAndFlush(User.builder().username("Other reader").email("other.confirmation@example.com")
                .passwordHash("test-hash").role(Role.CUSTOMER).build());
        mvc.perform(get("/api/checkout/orders/{id}", id).header("Authorization", "Bearer " + jwt.generateToken(other)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/checkout/orders/{id}", Long.MAX_VALUE).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/checkout/orders/0").header("Authorization", token)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/checkout").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        var body = request(); body.put("checkoutKey", "not-a-uuid"); submit(body, 400);
        body.put("checkoutKey", UUID.randomUUID().toString()); body.put("cartFingerprint", "invalid"); submit(body, 400);
    }

    private void assertUnchanged(int stock, int quantity) {
        assertThat(orders.count()).isZero();
        assertThat(products.findById(book.getId()).orElseThrow().getStockQuantity()).isEqualTo(stock);
        assertThat(carts.findByUserIdOrderByIdAsc(customer.getId())).singleElement().extracting(CartItem::getQuantity).isEqualTo(quantity);
    }
    private PaymentMethod card(User owner, YearMonth expiry) {
        return payments.saveAndFlush(PaymentMethod.builder().user(owner).provider("Visa").cardholderName("Demo Reader")
                .lastFour("1234").expiryMonth(expiry.getMonthValue()).expiryYear(expiry.getYear()).build());
    }
    private Long submit(Map<String, Object> body, int statusCode) throws Exception {
        String json = mvc.perform(post("/api/checkout").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body))).andExpect(status().is(statusCode))
                .andReturn().getResponse().getContentAsString();
        return statusCode == 201 ? mapper.readTree(json).get("id").asLong() : null;
    }

    private Map<String, Object> request() throws Exception {
        String review = mvc.perform(get("/api/checkout/review").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new HashMap<>(Map.of("addressId", address.getId(), "paymentType", "CASH_ON_DELIVERY",
                "checkoutKey", UUID.randomUUID().toString(), "cartFingerprint", mapper.readTree(review).get("cartFingerprint").asText()));
    }
}
