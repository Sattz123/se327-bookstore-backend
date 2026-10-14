package se327.bookstore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.CategoryDao;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.*;
import se327.bookstore.entity.BookFormat;
import se327.bookstore.entity.Category;
import se327.bookstore.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminProductServiceTests {

    @Mock
    private ProductDao products;

    @Mock
    private CategoryDao categories;

    @InjectMocks
    private AdminProductService service;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Fiction")
                .visible(true)
                .version(0L)
                .build();

        product = Product.builder()
                .id(10L)
                .name("The Hobbit")
                .description("A fantasy novel")
                .author("J.R.R. Tolkien")
                .price(new BigDecimal("19.99"))
                .stockQuantity(15)
                .visible(true)
                .category(category)
                .formats(new HashSet<>(List.of(BookFormat.PAPERBACK, BookFormat.HARDCOVER)))
                .images(new ArrayList<>(List.of("/images/hobbit.jpg")))
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void list_returnsPagedProducts() {
        Page<Product> page = new PageImpl<>(List.of(product));
        when(products.findAdminProducts(eq("Hobbit"), eq(1L), eq(true), any(Pageable.class))).thenReturn(page);

        AdminProductPageDto result = service.list(" Hobbit ", 1L, true, 1);

        assertThat(result.page()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().name()).isEqualTo("The Hobbit");
    }

    @Test
    void get_returnsProductDetail_whenFound() {
        when(products.findById(10L)).thenReturn(Optional.of(product));

        AdminProductDto dto = service.get(10L);

        assertThat(dto.id()).isEqualTo(10L);
        assertThat(dto.name()).isEqualTo("The Hobbit");
        assertThat(dto.formats()).containsExactlyInAnyOrder(BookFormat.HARDCOVER, BookFormat.PAPERBACK);
    }

    @Test
    void get_throwsNotFound_whenMissing() {
        when(products.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void categories_returnsAdminCategoryList() {
        when(categories.findAll()).thenReturn(List.of(category));

        List<AdminCategoryDto> result = service.categories();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(1L);
        assertThat(result.getFirst().name()).isEqualTo("Fiction");
    }

    @Test
    void create_createsNewProductWithFormats() {
        AdminProductRequest request = new AdminProductRequest("1984", "Dystopian novel", "George Orwell",
                new BigDecimal("15.50"), 20, 1L, Set.of(BookFormat.PAPERBACK), true, null);
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(products.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(20L);
            p.setVersion(0L);
            return p;
        });

        AdminProductDto dto = service.create(request);

        assertThat(dto.id()).isEqualTo(20L);
        assertThat(dto.name()).isEqualTo("1984");
        assertThat(dto.author()).isEqualTo("George Orwell");
        assertThat(dto.formats()).containsExactly(BookFormat.PAPERBACK);
        verify(products).save(any(Product.class));
    }

    @Test
    void update_updatesProductSuccessfully() {
        AdminProductRequest request = new AdminProductRequest("The Hobbit Revised", "Updated description", "J.R.R. Tolkien",
                new BigDecimal("21.99"), 25, 1L, Set.of(BookFormat.EBOOK), false, 0L);
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(products.save(product)).thenReturn(product);

        AdminProductDto dto = service.update(10L, request);

        assertThat(dto.name()).isEqualTo("The Hobbit Revised");
        assertThat(dto.price()).isEqualTo(new BigDecimal("21.99"));
        assertThat(dto.stockQuantity()).isEqualTo(25);
        assertThat(dto.visible()).isFalse();
        assertThat(dto.formats()).containsExactly(BookFormat.EBOOK);
        verify(products).save(product);
    }

    @Test
    void update_throwsBadRequest_whenVersionMissing() {
        AdminProductRequest request = new AdminProductRequest("The Hobbit", "Desc", "Author",
                new BigDecimal("20.00"), 10, 1L, Set.of(BookFormat.PAPERBACK), true, null);

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void update_throwsConflict_whenVersionMismatch() {
        AdminProductRequest request = new AdminProductRequest("The Hobbit", "Desc", "Author",
                new BigDecimal("20.00"), 10, 1L, Set.of(BookFormat.PAPERBACK), true, 9L);
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void update_throwsBadRequest_whenCategoryMissing() {
        AdminProductRequest request = new AdminProductRequest("The Hobbit", "Desc", "Author",
                new BigDecimal("20.00"), 10, 99L, Set.of(BookFormat.PAPERBACK), true, 0L);
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(categories.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(10L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void inventory_updatesStockQuantitySuccessfully() {
        AdminInventoryRequest request = new AdminInventoryRequest(50, 0L);
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(products.save(product)).thenReturn(product);

        AdminProductDto dto = service.inventory(10L, request);

        assertThat(dto.stockQuantity()).isEqualTo(50);
        verify(products).save(product);
    }

    @Test
    void visibility_togglesVisibilitySuccessfully() {
        AdminVisibilityRequest request = new AdminVisibilityRequest(false, 0L);
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(products.save(product)).thenReturn(product);

        AdminProductDto dto = service.visibility(10L, request);

        assertThat(dto.visible()).isFalse();
        verify(products).save(product);
    }

    @Test
    void archive_setsVisibleToFalse() {
        when(products.findByIdForUpdate(10L)).thenReturn(Optional.of(product));
        when(products.save(product)).thenReturn(product);

        AdminProductDto dto = service.archive(10L, 0L);

        assertThat(dto.visible()).isFalse();
        verify(products).save(product);
    }
}
