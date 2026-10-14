package se327.bookstore.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.AdminProductDto;
import se327.bookstore.entity.Product;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductImageServiceTests {

    @Mock
    private ProductDao products;

    @Mock
    private ProductImageStorage storage;

    @Mock
    private AdminProductService adminProducts;

    @InjectMocks
    private ProductImageService service;

    private Product product;
    private AdminProductDto productDto;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        product = Product.builder()
                .id(1L)
                .name("Sample Book")
                .version(0L)
                .images(new ArrayList<>(List.of("/images/1.jpg", "/images/2.jpg")))
                .build();

        productDto = mock(AdminProductDto.class);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void upload_appendsImage_whenIndexIsNull() {
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2});
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(storage.store(file)).thenReturn("/images/uploads/products/new.png");
        when(adminProducts.get(1L)).thenReturn(productDto);

        AdminProductDto result = service.upload(1L, 0L, null, file);

        assertThat(result).isSameAs(productDto);
        assertThat(product.getImages()).contains("/images/uploads/products/new.png");
        verify(products).save(product);
    }

    @Test
    void upload_replacesImage_whenIndexProvided() {
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2});
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(storage.store(file)).thenReturn("/images/uploads/products/replaced.png");
        when(adminProducts.get(1L)).thenReturn(productDto);

        AdminProductDto result = service.upload(1L, 0L, 1, file);

        assertThat(result).isSameAs(productDto);
        assertThat(product.getImages().get(1)).isEqualTo("/images/uploads/products/replaced.png");
        verify(products).save(product);
    }

    @Test
    void upload_throwsBadRequest_whenProductAlreadyHasMaxImages() {
        product.setImages(new ArrayList<>(List.of("1", "2", "3", "4", "5")));
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2});
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.upload(1L, 0L, null, file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void upload_throwsNotFound_whenIndexOutOfBounds() {
        MultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[]{1, 2});
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.upload(1L, 0L, 99, file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void remove_removesImageAtIndex() {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(adminProducts.get(1L)).thenReturn(productDto);

        AdminProductDto result = service.remove(1L, 0L, 0);

        assertThat(result).isSameAs(productDto);
        assertThat(product.getImages()).hasSize(1);
        assertThat(product.getImages().getFirst()).isEqualTo("/images/2.jpg");
        verify(products).save(product);
    }

    @Test
    void remove_throwsNotFound_whenIndexInvalid() {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.remove(1L, 0L, -1))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void remove_throwsConflict_whenVersionMismatch() {
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service.remove(1L, 5L, 0))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
