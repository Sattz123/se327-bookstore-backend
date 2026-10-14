package se327.bookstore.entity;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.hikari.schema=development_seed_test",
        "spring.flyway.default-schema=development_seed_test",
        "spring.flyway.schemas=development_seed_test",
        "spring.jpa.properties.hibernate.default_schema=development_seed_test"
})
@ActiveProfiles("dev")
@Transactional
class DevelopmentSeedTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Test
    void seedsFiveCategoriesAndTwelveBooksPerCategory() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM categories", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM products", Integer.class)).isEqualTo(60);
        assertThat(jdbcTemplate.queryForList(
                "SELECT count(*) FROM products GROUP BY category_id ORDER BY category_id", Integer.class))
                .containsExactly(12, 12, 12, 12, 12);
        assertThat(jdbcTemplate.queryForList("SELECT name FROM categories ORDER BY id", String.class))
                .containsExactly("Fiction", "Children", "Non-Fiction", "Mystery & Thriller", "Science & Technology");
    }

    @Test
    void preservesOriginalDetailsIncludingApostrophesAndLastBook() {
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM products WHERE id = 13", String.class))
                .isEqualTo("Charlotte's Web");
        assertThat(jdbcTemplate.queryForObject("SELECT author FROM products WHERE id = 1", String.class))
                .isEqualTo("F. Scott Fitzgerald");
        assertThat(jdbcTemplate.queryForObject("SELECT price FROM products WHERE id = 10", BigDecimal.class))
                .isEqualByComparingTo("19.99");
        assertThat(jdbcTemplate.queryForObject("SELECT stock_quantity FROM products WHERE id = 1", Integer.class))
                .isEqualTo(74);
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM products WHERE id = 60", String.class))
                .isEqualTo("Algorithms to Live By");
    }

    @Test
    void seedsFormatsAndAvailableImageResources() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM product_formats", Integer.class)).isEqualTo(180);
        assertThat(jdbcTemplate.queryForList(
                "SELECT format FROM product_formats WHERE product_id = 1 ORDER BY format", String.class))
                .containsExactly("EBOOK", "HARDCOVER", "PAPERBACK");

        List<String> images = jdbcTemplate.queryForList("SELECT image_url FROM product_images", String.class);
        assertThat(images).hasSize(60);
        for (String image : images) {
            assertThat(new ClassPathResource("dev/static" + image).exists()).isTrue();
        }
    }

    @Test
    void advancesIdentitySequencesPastTheOriginalIds() {
        Long categoryId = jdbcTemplate.queryForObject(
                "INSERT INTO categories (name) VALUES ('New category') RETURNING id", Long.class);
        Long productId = jdbcTemplate.queryForObject(
                "INSERT INTO products (category_id, name, price) VALUES (?, 'New book', 12.00) RETURNING id",
                Long.class, categoryId);

        assertThat(categoryId).isGreaterThan(5L);
        assertThat(productId).isGreaterThan(60L);
    }

    @Test
    void restoresTheMissingSampleCoverWithoutOverwritingACustomCover() throws java.io.IOException {
        String repair = new ClassPathResource("dev/db/migration/V14__restore_missing_algorithms_cover.sql")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Long version = jdbcTemplate.queryForObject("SELECT version FROM products WHERE id = 60", Long.class);
        jdbcTemplate.update("DELETE FROM product_images WHERE product_id = 60");
        jdbcTemplate.execute(repair);
        assertThat(jdbcTemplate.queryForObject("SELECT image_url FROM product_images WHERE product_id = 60", String.class))
                .isEqualTo("/images/products/sci-tech-12.png");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM products WHERE id = 60", Long.class))
                .isEqualTo(version + 1);
        jdbcTemplate.update("UPDATE product_images SET image_url = '/images/custom.jpg' WHERE product_id = 60");
        jdbcTemplate.execute(repair);
        assertThat(jdbcTemplate.queryForObject("SELECT image_url FROM product_images WHERE product_id = 60", String.class))
                .isEqualTo("/images/custom.jpg");
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM products WHERE id = 60", Long.class))
                .isEqualTo(version + 1);
    }

    @Test
    void doesNotReapplyTheSeedOrOverwriteAnEditedBook() {
        jdbcTemplate.update("UPDATE products SET name = 'Edited title', stock_quantity = 3 WHERE id = 1");

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM products WHERE id = 1", String.class))
                .isEqualTo("Edited title");
        assertThat(jdbcTemplate.queryForObject("SELECT stock_quantity FROM products WHERE id = 1", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void seedsDevelopmentAdminAndCustomerAccounts() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE role = 'ADMIN'", String.class))
                .isEqualTo("admin@bookstore.com");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT email FROM users WHERE role = 'CUSTOMER'", String.class))
                .isEqualTo("customer@bookstore.com");

        Long newUserId = jdbcTemplate.queryForObject(
                "INSERT INTO users (username, email, password_hash, role) VALUES ('Extra', 'extra@example.com', 'hash', 'CUSTOMER') RETURNING id",
                Long.class);
        assertThat(newUserId).isGreaterThan(2L);
    }
}
