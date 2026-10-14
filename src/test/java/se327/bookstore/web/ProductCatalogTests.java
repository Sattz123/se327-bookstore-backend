package se327.bookstore.web;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.entity.BookFormat;
import se327.bookstore.entity.Category;
import se327.bookstore.entity.Product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=catalog_api_test",
        "spring.flyway.default-schema=catalog_api_test",
        "spring.flyway.schemas=catalog_api_test",
        "spring.jpa.properties.hibernate.default_schema=catalog_api_test",
        "app.frontend-origin=http://localhost:5173"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ProductCatalogTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Test
    void listsNewestBooksUsingPublicDtos() throws Exception {
        Product first = saveBook("First book");
        Product second = saveBook("Second book");

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(second.getId()))
                .andExpect(jsonPath("$.content[1].id").value(first.getId()))
                .andExpect(jsonPath("$.content[0].category.name").value("Test category"))
                .andExpect(jsonPath("$.content[0].visible").doesNotExist())
                .andExpect(jsonPath("$.content[0].category.visible").doesNotExist());
    }

    @Test
    void returnsDetailsAndOrderedCollectionsFromPostgres() throws Exception {
        Product book = saveBook("Charlotte's Web");

        mvc.perform(get("/api/products/{id}", book.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.name").value("Charlotte's Web"))
                .andExpect(jsonPath("$.author").value("Test author"))
                .andExpect(jsonPath("$.description").value("A book description."))
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.stockQuantity").value(7))
                .andExpect(jsonPath("$.category.id").value(book.getCategory().getId()))
                .andExpect(jsonPath("$.category.imageUrl").value("/images/category.png"))
                .andExpect(jsonPath("$.images[0]").value("/images/front.png"))
                .andExpect(jsonPath("$.images[1]").value("/images/back.png"))
                .andExpect(jsonPath("$.formats[0]").value("PAPERBACK"))
                .andExpect(jsonPath("$.formats[1]").value("EBOOK"));
    }

    @Test
    void returnsProblemDetailForMissingBook() throws Exception {
        expectMissing(Long.MAX_VALUE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidBookIds(String id) throws Exception {
        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void hidesInvisibleBooksFromBothEndpoints() throws Exception {
        Product book = saveBook("Hidden book");
        Product hidden = entityManager.find(Product.class, book.getId());
        hidden.setVisible(false);
        flushAndClear();

        expectEmptyList();
        expectMissing(book.getId());
    }

    @Test
    void hidesBooksInInvisibleCategoriesFromBothEndpoints() throws Exception {
        Product book = saveBook("Hidden category book");
        Category category = entityManager.find(Category.class, book.getCategory().getId());
        category.setVisible(false);
        flushAndClear();

        expectEmptyList();
        expectMissing(book.getId());
    }

    @Test
    void keepsOutOfStockBooksViewable() throws Exception {
        Product book = saveBook("Sold out book");
        entityManager.find(Product.class, book.getId()).setStockQuantity(0);
        flushAndClear();

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].stockQuantity").value(0));
        mvc.perform(get("/api/products/{id}", book.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(0));
    }

    @Test
    void returnsAnEmptyPageForAnEmptyCatalog() throws Exception {
        expectEmptyList();
    }

    @Test
    void supportsOptionalDetailsAndEmptyCollections() throws Exception {
        Category category = Category.builder().name("Minimal category").build();
        entityManager.persist(category);
        Product book = Product.builder().category(category).name("Minimal book").price(BigDecimal.ZERO).build();
        entityManager.persist(book);
        flushAndClear();

        mvc.perform(get("/api/products/{id}", book.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.author").isEmpty())
                .andExpect(jsonPath("$.description").isEmpty())
                .andExpect(jsonPath("$.category.imageUrl").isEmpty())
                .andExpect(jsonPath("$.images").isEmpty())
                .andExpect(jsonPath("$.formats").isEmpty());
    }

    @Test
    void allowsTheConfiguredFrontendToReadTheCatalog() throws Exception {
        mvc.perform(get("/api/products").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    private Product saveBook(String name) {
        Category category = Category.builder().name("Test category").imageUrl("/images/category.png").build();
        entityManager.persist(category);
        Product book = Product.builder().category(category).name(name).author("Test author")
                .description("A book description.").price(new BigDecimal("19.99")).stockQuantity(7)
                .images(new ArrayList<>(List.of("/images/front.png", "/images/back.png")))
                .formats(new HashSet<>(Set.of(BookFormat.EBOOK, BookFormat.PAPERBACK))).build();
        entityManager.persist(book);
        flushAndClear();
        return book;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private void expectEmptyList() throws Exception {
        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private void expectMissing(Long id) throws Exception {
        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Book not found."))
                .andExpect(jsonPath("$.instance").value("/api/products/" + id));
    }
}
