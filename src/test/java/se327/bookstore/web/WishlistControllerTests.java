package se327.bookstore.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.dao.UserDao;
import se327.bookstore.dao.WishlistDao;
import se327.bookstore.dto.AddToWishlistRequest;
import se327.bookstore.entity.Category;
import se327.bookstore.entity.Product;
import se327.bookstore.entity.Role;
import se327.bookstore.entity.User;
import se327.bookstore.entity.WishlistItem;
import se327.bookstore.repository.CategoryRepository;
import se327.bookstore.repository.ProductRepository;
import se327.bookstore.security.JwtTokenService;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WishlistControllerTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserDao userDao;

    @Autowired
    private WishlistDao wishlistDao;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private User createCustomer(String username, String email) {
        User user = User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode("Password123!"))
                .role(Role.CUSTOMER)
                .build();
        return userDao.save(user);
    }

    private Product createProduct(String name) {
        Category category = categoryRepository.findAll().stream().findFirst().orElseGet(() -> {
            Category c = Category.builder().name("General").build();
            return categoryRepository.save(c);
        });

        Product product = Product.builder()
                .name(name)
                .description("Sample description")
                .price(new BigDecimal("29.99"))
                .author("Famous Author")
                .stockQuantity(10)
                .visible(true)
                .category(category)
                .build();
        return productRepository.save(product);
    }

    @Test
    void rejectsUnauthenticatedWishlistAccess() throws Exception {
        mvc.perform(get("/api/wishlist"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mvc.perform(post("/api/wishlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddToWishlistRequest(1L))))
                .andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/wishlist/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsEmptyWishlistForNewUser() throws Exception {
        User user = createCustomer("Empty Wishlist", "empty.wish@example.com");
        String token = jwtTokenService.generateToken(user);

        mvc.perform(get("/api/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void addsBookToWishlistSuccessfully() throws Exception {
        User user = createCustomer("Wishlist Adder", "adder@example.com");
        Product product = createProduct("Kotlin in Action");
        String token = jwtTokenService.generateToken(user);

        AddToWishlistRequest request = new AddToWishlistRequest(product.getId());

        mvc.perform(post("/api/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.product.id").value(product.getId()))
                .andExpect(jsonPath("$.product.name").value("Kotlin in Action"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(wishlistDao.existsByUserIdAndProductId(user.getId(), product.getId())).isTrue();
    }

    @Test
    void preventsDuplicateSavedProductsWithConflictResponse() throws Exception {
        User user = createCustomer("Duplicate Saver", "dupsaver@example.com");
        Product product = createProduct("Clean Architecture");
        String token = jwtTokenService.generateToken(user);

        WishlistItem item = WishlistItem.builder().user(user).product(product).build();
        wishlistDao.save(item);

        AddToWishlistRequest request = new AddToWishlistRequest(product.getId());

        mvc.perform(post("/api/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Duplicate Wishlist Item"))
                .andExpect(jsonPath("$.detail").value("Book is already in your wishlist."));
    }

    @Test
    void rejectsAddingNonExistentProductToWishlist() throws Exception {
        User user = createCustomer("NonExistent Product Saver", "noprod@example.com");
        String token = jwtTokenService.generateToken(user);

        AddToWishlistRequest request = new AddToWishlistRequest(999999L);

        mvc.perform(post("/api/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsAddingWithNullProductId() throws Exception {
        User user = createCustomer("Bad Request User", "badrequest@example.com");
        String token = jwtTokenService.generateToken(user);

        Map<String, Object> badRequest = Map.of();

        mvc.perform(post("/api/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void removesBookFromWishlistSuccessfully() throws Exception {
        User user = createCustomer("Remover", "remover@example.com");
        Product product = createProduct("Refactoring");
        String token = jwtTokenService.generateToken(user);

        WishlistItem item = WishlistItem.builder().user(user).product(product).build();
        wishlistDao.save(item);

        mvc.perform(delete("/api/wishlist/" + product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        assertThat(wishlistDao.existsByUserIdAndProductId(user.getId(), product.getId())).isFalse();
    }

    @Test
    void rejectsRemovingBookNotCurrentlyInWishlist() throws Exception {
        User user = createCustomer("Not In Wishlist User", "notinwish@example.com");
        Product product = createProduct("Domain-Driven Design");
        String token = jwtTokenService.generateToken(user);

        mvc.perform(delete("/api/wishlist/" + product.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void enforcesWishlistAccessOwnershipBetweenDifferentCustomers() throws Exception {
        User userA = createCustomer("Customer A", "customera@example.com");
        User userB = createCustomer("Customer B", "customerb@example.com");

        Product productA = createProduct("Book for A");
        Product productB = createProduct("Book for B");

        wishlistDao.save(WishlistItem.builder().user(userA).product(productA).build());
        wishlistDao.save(WishlistItem.builder().user(userB).product(productB).build());

        String tokenA = jwtTokenService.generateToken(userA);
        String tokenB = jwtTokenService.generateToken(userB);

        // User A only sees Book for A
        mvc.perform(get("/api/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].product.name").value("Book for A"));

        // User B only sees Book for B
        mvc.perform(get("/api/wishlist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].product.name").value("Book for B"));

        // User A cannot delete User B's saved book from A's wishlist
        mvc.perform(delete("/api/wishlist/" + productB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isNotFound());

        // Verify User B's wishlist still intact
        assertThat(wishlistDao.existsByUserIdAndProductId(userB.getId(), productB.getId())).isTrue();
    }
}
