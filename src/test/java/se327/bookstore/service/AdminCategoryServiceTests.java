package se327.bookstore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.CategoryDao;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.AdminCategoryDetailDto;
import se327.bookstore.dto.AdminCategoryRequest;
import se327.bookstore.dto.AdminVisibilityRequest;
import se327.bookstore.entity.Category;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCategoryServiceTests {

    @Mock
    private CategoryDao categories;

    @Mock
    private ProductDao products;

    @Mock
    private ProductImageStorage images;

    @InjectMocks
    private AdminCategoryService service;

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(1L)
                .name("Computer Science")
                .imageUrl("/images/cs.jpg")
                .visible(true)
                .version(0L)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void list_returnsFilteredCategories() {
        Category cat2 = Category.builder().id(2L).name("Mathematics").visible(true).version(1L).build();
        when(categories.findAll()).thenReturn(List.of(category, cat2));
        when(products.countByCategoryId(1L)).thenReturn(5L);

        List<AdminCategoryDetailDto> result = service.list("computer");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("Computer Science");
        assertThat(result.getFirst().productCount()).isEqualTo(5L);
    }

    @Test
    void get_returnsCategoryDetail_whenFound() {
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(products.countByCategoryId(1L)).thenReturn(3L);

        AdminCategoryDetailDto dto = service.get(1L);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.name()).isEqualTo("Computer Science");
        assertThat(dto.productCount()).isEqualTo(3L);
    }

    @Test
    void get_throwsNotFound_whenMissing() {
        when(categories.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void save_createsNewCategory_whenIdIsNull() {
        AdminCategoryRequest request = new AdminCategoryRequest("Fiction", null, true, null);
        when(categories.save(any(Category.class))).thenAnswer(invocation -> {
            Category c = invocation.getArgument(0);
            c.setId(10L);
            c.setVersion(0L);
            return c;
        });
        when(products.countByCategoryId(10L)).thenReturn(0L);

        AdminCategoryDetailDto result = service.save(null, request, null);

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.name()).isEqualTo("Fiction");
        assertThat(result.visible()).isTrue();
        verify(categories).save(any(Category.class));
    }

    @Test
    void save_updatesCategory_whenIdProvided() {
        AdminCategoryRequest request = new AdminCategoryRequest("Updated CS", "https://example.com/cs.png", false, 0L);
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(categories.save(category)).thenReturn(category);
        when(products.countByCategoryId(1L)).thenReturn(4L);

        AdminCategoryDetailDto result = service.save(1L, request, null);

        assertThat(result.name()).isEqualTo("Updated CS");
        assertThat(result.imageUrl()).isEqualTo("https://example.com/cs.png");
        assertThat(result.visible()).isFalse();
        verify(categories).save(category);
    }

    @Test
    void save_throwsBadRequest_whenVersionMissingForExistingCategory() {
        AdminCategoryRequest request = new AdminCategoryRequest("CS", null, true, null);

        assertThatThrownBy(() -> service.save(1L, request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void save_throwsConflict_whenVersionMismatch() {
        AdminCategoryRequest request = new AdminCategoryRequest("CS", null, true, 5L);
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.save(1L, request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void save_storesUploadedImage_whenFileProvided() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            MultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3});
            AdminCategoryRequest request = new AdminCategoryRequest("Tech", null, true, 0L);
            when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
            when(images.store(file)).thenReturn("/images/uploaded-uuid.jpg");
            when(categories.save(category)).thenReturn(category);

            AdminCategoryDetailDto result = service.save(1L, request, file);

            assertThat(result.imageUrl()).isEqualTo("/images/uploaded-uuid.jpg");
            verify(images).store(file);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void save_throwsBadRequest_whenInvalidImageUrl() {
        AdminCategoryRequest request = new AdminCategoryRequest("Tech", "ftp://invalid-url.com", true, 0L);
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.save(1L, request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void visibility_togglesVisibilitySuccessfully() {
        AdminVisibilityRequest request = new AdminVisibilityRequest(false, 0L);
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(categories.save(category)).thenReturn(category);
        when(products.countByCategoryId(1L)).thenReturn(2L);

        AdminCategoryDetailDto result = service.visibility(1L, request);

        assertThat(result.visible()).isFalse();
        verify(categories).save(category);
    }

    @Test
    void delete_deletesCategory_whenNoProductsLinked() {
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(products.countByCategoryId(1L)).thenReturn(0L);

        service.delete(1L, 0L);

        verify(categories).delete(category);
    }

    @Test
    void delete_throwsConflict_whenProductsLinked() {
        when(categories.findByIdForUpdate(1L)).thenReturn(Optional.of(category));
        when(products.countByCategoryId(1L)).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(1L, 0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).contains("3 product(s)");
                });

        verify(categories, never()).delete(any());
    }

    @Test
    void delete_throwsNotFound_whenCategoryNotFound() {
        when(categories.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L, 0L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
