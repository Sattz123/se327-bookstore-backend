package se327.bookstore.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductImageStorageTests {

    @TempDir
    Path tempDir;

    private ProductImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ProductImageStorage(tempDir.toString());
    }

    @Test
    void resourceLocation_returnsFileUriWithTrailingSlash() {
        String location = storage.resourceLocation();
        assertThat(location).startsWith("file:/").endsWith("/");
    }

    @Test
    void store_storesValidPngImage() throws IOException {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", os.toByteArray());

        String url = storage.store(file);

        assertThat(url).startsWith(ProductImageStorage.URL_PREFIX).endsWith(".png");
        String filename = url.substring(ProductImageStorage.URL_PREFIX.length());
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void store_storesValidJpegImage() throws IOException {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", os);

        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", os.toByteArray());

        String url = storage.store(file);

        assertThat(url).startsWith(ProductImageStorage.URL_PREFIX).endsWith(".jpg");
        String filename = url.substring(ProductImageStorage.URL_PREFIX.length());
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();
    }

    @Test
    void store_throwsBadRequest_whenEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void store_throwsBadRequest_whenCorruptBytes() {
        MockMultipartFile file = new MockMultipartFile("file", "corrupt.png", "image/png", new byte[]{1, 2, 3, 4, 5});

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void store_throwsBadRequest_whenMismatchedContentType() throws IOException {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", os);

        // Content claims to be PNG, but actual bytes are JPEG
        MockMultipartFile file = new MockMultipartFile("file", "mismatch.png", "image/png", os.toByteArray());

        assertThatThrownBy(() -> storage.store(file))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void deleteManaged_deletesExistingManagedFile() throws IOException {
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        ImageIO.write(image, "png", os);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", os.toByteArray());

        String url = storage.store(file);
        String filename = url.substring(ProductImageStorage.URL_PREFIX.length());
        assertThat(Files.exists(tempDir.resolve(filename))).isTrue();

        storage.deleteManaged(url);
        assertThat(Files.exists(tempDir.resolve(filename))).isFalse();
    }

    @Test
    void deleteManaged_safelyIgnoresNullOrExternalUrls() {
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            storage.deleteManaged(null);
            storage.deleteManaged("https://example.com/image.png");
            storage.deleteManaged("/images/not-a-product/pic.png");
            storage.deleteManaged("/images/uploads/products/../../evil.png");
        });
    }
}
