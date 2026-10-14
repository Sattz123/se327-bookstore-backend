package se327.bookstore.entity;

import jakarta.persistence.EntityManager;
import jakarta.validation.ConstraintViolationException;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class CatalogPersistenceTests {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void persistsBookDetailsCategoryAndOrderedImages() {
        Product product = createProduct();
        product.setImages(new ArrayList<>(List.of("front.png", "back.png")));
        product.setFormats(new HashSet<>(Set.of(BookFormat.PAPERBACK, BookFormat.EBOOK)));
        entityManager.persist(product);
        entityManager.flush();
        entityManager.clear();

        Product saved = entityManager.find(Product.class, product.getId());
        assertThat(saved.getCategory().getName()).isEqualTo("Fiction");
        assertThat(saved.getCategory().getImageUrl()).isEqualTo("fiction.png");
        assertThat(saved.getCategory().getCreatedAt()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Test Book");
        assertThat(saved.getAuthor()).isEqualTo("Test Author");
        assertThat(saved.getDescription()).isEqualTo("A test book description.");
        assertThat(saved.getPrice()).isEqualByComparingTo("18.50");
        assertThat(saved.getStockQuantity()).isEqualTo(12);
        assertThat(saved.isVisible()).isTrue();
        assertThat(saved.getImages()).containsExactly("front.png", "back.png");
        assertThat(saved.getFormats()).containsExactlyInAnyOrder(BookFormat.PAPERBACK, BookFormat.EBOOK);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void persistsStockAndVisibilityChanges() {
        Product product = createProduct();
        entityManager.persist(product);
        entityManager.flush();
        entityManager.clear();

        Product saved = entityManager.find(Product.class, product.getId());
        saved.setStockQuantity(0);
        saved.setVisible(false);
        saved.getCategory().setVisible(false);
        entityManager.flush();
        entityManager.clear();

        Product updated = entityManager.find(Product.class, product.getId());
        assertThat(updated.getStockQuantity()).isZero();
        assertThat(updated.isVisible()).isFalse();
        assertThat(updated.getCategory().isVisible()).isFalse();
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(updated.getCreatedAt());
    }

    @ParameterizedTest
    @CsvSource({"-1.00, 1", "18.50, -1"})
    void databaseRejectsNegativePricesOrStock(BigDecimal price, int stock) {
        Category category = createCategory();
        long categoryId = category.getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO products (category_id, name, price, stock_quantity) VALUES (?, ?, ?, ?)",
                categoryId, "Invalid book", price, stock))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsDeletingACategoryThatStillHasBooks() {
        Product product = createProduct();
        entityManager.persist(product);
        entityManager.flush();
        long categoryId = product.getCategory().getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "DELETE FROM categories WHERE id = ?", categoryId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsUnknownBookFormats() {
        Product product = createProduct();
        entityManager.persist(product);
        entityManager.flush();
        long productId = product.getId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO product_formats (product_id, format) VALUES (?, ?)",
                productId, "UNKNOWN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMoreThanFiveImages() {
        Product product = createProduct();
        product.setImages(new ArrayList<>(List.of("1.png", "2.png", "3.png", "4.png", "5.png", "6.png")));

        assertThatThrownBy(() -> entityManager.persist(product)).isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void appliesTheMigrationOnceAndDoesNotRecreateTables() {
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getScript)
                .contains("V1__create_catalog_tables.sql");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    private Category createCategory() {
        Category category = Category.builder().name("Fiction").imageUrl("fiction.png").build();
        entityManager.persist(category);
        entityManager.flush();
        return category;
    }

    private Product createProduct() {
        return Product.builder()
                .category(createCategory())
                .name("Test Book")
                .author("Test Author")
                .description("A test book description.")
                .price(new BigDecimal("18.50"))
                .stockQuantity(12)
                .build();
    }
}
