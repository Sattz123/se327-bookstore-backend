package se327.bookstore.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

@Component
public class ProductImageStorage {
    public static final String URL_PREFIX = "/images/uploads/products/";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Logger log = LoggerFactory.getLogger(ProductImageStorage.class);
    private final Path directory;

    public ProductImageStorage(@Value("${app.images.directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }
    public String resourceLocation() {
        String location = directory.toUri().toString();
        return location.endsWith("/") ? location : location + "/";
    }

    private record DecodedImage(byte[] data, String extension) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DecodedImage(byte[] otherData, String otherExt))) return false;
            return java.util.Arrays.equals(data, otherData) && java.util.Objects.equals(extension, otherExt);
        }

        @Override
        public int hashCode() {
            return 31 * java.util.Arrays.hashCode(data) + java.util.Objects.hashCode(extension);
        }

        @Override
        public String toString() {
            return "DecodedImage[extension=" + extension + ", size=" + (data != null ? data.length : 0) + "]";
        }
    }

    public String store(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw invalid("Choose a PNG or JPEG image no larger than 5 MB.");
        DecodedImage decoded;
        try {
            decoded = decodeAndValidate(file);
        } catch (IOException ex) {
            throw invalid("The image could not be decoded. Choose a valid PNG or JPEG.");
        }
        Path target = null;
        try {
            Files.createDirectories(directory);
            String filename = UUID.randomUUID() + "." + decoded.extension();
            target = directory.resolve(filename);
            Files.write(target, decoded.data(), StandardOpenOption.CREATE_NEW);
            return URL_PREFIX + filename;
        } catch (IOException ex) {
            if (target != null) deleteFile(target);
            throw new IllegalStateException("Unable to store product image.", ex);
        }
    }

    private DecodedImage decodeAndValidate(MultipartFile file) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(file.getBytes()))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("The file is not a readable PNG or JPEG image.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                    throw invalid("Only PNG and JPEG images are supported.");
                }
                String extension = format.equals("png") ? "png" : "jpg";
                String expectedType = extension.equals("png") ? "image/png" : "image/jpeg";
                if (!expectedType.equalsIgnoreCase(file.getContentType())) {
                    throw invalid("The image contents must match its PNG or JPEG content type.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16777216) {
                    throw invalid("Images must be between 1 and 4096 pixels in each dimension.");
                }
                var image = reader.read(0);
                byte[] normalized;
                try (var output = new ByteArrayOutputStream()) {
                    if (image == null || !ImageIO.write(image, extension, output)) throw invalid("Unable to read this image.");
                    normalized = output.toByteArray();
                } finally {
                    if (image != null) image.flush();
                }
                if (normalized.length > MAX_BYTES) throw invalid("The normalized image exceeds 5 MB. Choose a smaller image.");
                return new DecodedImage(normalized, extension);
            } finally {
                reader.dispose();
            }
        }
    }

    public void deleteManaged(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) return;
        String filename = url.substring(URL_PREFIX.length());
        if (!filename.matches("[a-f0-9-]{36}\\.(png|jpg)")) return;
        Path target = directory.resolve(filename).normalize();
        if (target.startsWith(directory)) deleteFile(target);
    }
    private void deleteFile(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ex) { log.warn("Unable to remove unused product image {}", path.getFileName()); }
    }
    private ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
