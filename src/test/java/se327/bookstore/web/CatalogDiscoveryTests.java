package se327.bookstore.web;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import se327.bookstore.entity.Category;
import se327.bookstore.entity.Product;

import java.math.BigDecimal;
import java.util.ArrayList;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=discovery_api_test",
        "spring.flyway.default-schema=discovery_api_test",
        "spring.flyway.schemas=discovery_api_test",
        "spring.jpa.properties.hibernate.default_schema=discovery_api_test"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class CatalogDiscoveryTests {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void paginatesInTheDatabaseWithSixteenBooksAndStableOrdering() throws Exception {
        Category category = category("Fiction", true);
        var books = new ArrayList<Product>();
        for (int i = 1; i <= 36; i++) books.add(book(category, "Book " + i, null, true));
        sameCreationTime();

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(16))
                .andExpect(jsonPath("$.content[0].id").value(books.get(35).getId()))
                .andExpect(jsonPath("$.content[15].id").value(books.get(20).getId()))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(16))
                .andExpect(jsonPath("$.totalElements").value(36))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/products").param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(16))
                .andExpect(jsonPath("$.content[0].id").value(books.get(19).getId()))
                .andExpect(jsonPath("$.content[15].id").value(books.get(4).getId()));
        mvc.perform(get("/api/products").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4));
        mvc.perform(get("/api/products").param("page", "4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.page").value(4))
                .andExpect(jsonPath("$.totalElements").value(36));
    }

    @Test
    void combinesTrimmedCaseInsensitiveTitleOrAuthorSearchWithCategoryAndPage() throws Exception {
        Category science = category("Science", true);
        Category other = category("Other", true);
        for (int i = 0; i < 18; i++) book(science, i % 2 == 0 ? "Space book" : "Other title", "Space author", true);
        book(science, "Space hidden", null, false);
        book(science, "No match", null, true);
        book(other, "Space in another category", null, true);
        sameCreationTime();

        mvc.perform(get("/api/products").param("q", "  sPaCe  ")
                        .param("categoryId", science.getId().toString()).param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].category.id").value(science.getId()))
                .andExpect(jsonPath("$.totalElements").value(18))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\"})
    void treatsSearchWildcardsAsLiteralCharacters(String query) throws Exception {
        Category category = category("Fiction", true);
        book(category, "100%_\\complete", null, true);
        book(category, "Ordinary book", null, true);
        mvc.perform(get("/api/products").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("100%_\\complete"));
    }

    @Test
    void returnsEmptyMetadataWhenNoBooksMatch() throws Exception {
        book(category("Fiction", true), "Charlotte's Web", null, true);
        mvc.perform(get("/api/products").param("q", "unmatched title"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
        mvc.perform(get("/api/products").param("q", "charlotte's"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "1.5", "1000001", "2147483648"})
    void rejectsInvalidPages(String page) throws Exception {
        mvc.perform(get("/api/products").param("page", page))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808"})
    void rejectsInvalidCategoryFilters(String id) throws Exception {
        mvc.perform(get("/api/products").param("categoryId", id)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/categories/{id}", id)).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsSearchTextLongerThanOneHundredCharacters() throws Exception {
        mvc.perform(get("/api/products").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsVisibleCategoriesIncludingEmptyOnesAndRejectsHiddenOrMissingCategories() throws Exception {
        Category visible = category("Visible", true);
        Category empty = category("Empty", true);
        Category hidden = category("Hidden", false);
        book(hidden, "Hidden category book", null, true);

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(visible.getId()))
                .andExpect(jsonPath("$[1].id").value(empty.getId()))
                .andExpect(jsonPath("$[0].visible").doesNotExist());
        mvc.perform(get("/api/categories/{id}", visible.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Visible"));
        mvc.perform(get("/api/products").param("categoryId", empty.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        for (Long id : new Long[]{hidden.getId(), Long.MAX_VALUE}) {
            mvc.perform(get("/api/categories/{id}", id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("Category not found."));
            mvc.perform(get("/api/products").param("categoryId", id.toString()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void featuresOnlyTheNewestEightVisibleBooksUsingCreationTimeThenId() throws Exception {
        Category visible = category("Visible", true);
        var books = new ArrayList<Product>();
        for (int i = 0; i < 10; i++) books.add(book(visible, "Visible " + i, null, true));
        book(visible, "Hidden book", null, false);
        book(category("Hidden", false), "Hidden category book", null, true);
        sameCreationTime();
        jdbcTemplate.update("UPDATE products SET created_at = '2030-01-01T00:00:00Z' WHERE id = ?", books.getFirst().getId());

        mvc.perform(get("/api/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].id").value(books.getFirst().getId()))
                .andExpect(jsonPath("$[1].id").value(books.getLast().getId()))
                .andExpect(jsonPath("$[7].id").value(books.get(3).getId()));
    }

    @Test
    void supportsAnEmptyHomeAndCategoryList() throws Exception {
        mvc.perform(get("/api/products/featured")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/categories")).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    private Category category(String name, boolean visible) {
        Category category = Category.builder().name(name).visible(visible).build();
        entityManager.persist(category);
        return category;
    }

    private Product book(Category category, String name, String author, boolean visible) {
        Product product = Product.builder().category(category).name(name).author(author)
                .price(new BigDecimal("18.00")).visible(visible).build();
        entityManager.persist(product);
        entityManager.flush();
        return product;
    }

    private void sameCreationTime() {
        entityManager.flush();
        jdbcTemplate.update("UPDATE products SET created_at = '2020-01-01T00:00:00Z'");
        entityManager.clear();
    }
}
