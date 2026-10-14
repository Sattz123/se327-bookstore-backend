package se327.bookstore.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import se327.bookstore.dto.AdminProductDto;
import se327.bookstore.service.ProductImageService;

@RestController @RequestMapping("/api/admin/products/{id}/images") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ProductImageController {
    private final ProductImageService service;
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public AdminProductDto upload(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long version,
                                   @RequestPart("file") MultipartFile file) { return service.upload(id, version, null, file); }
    @PostMapping(value = "/{index}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminProductDto replace(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long version,
                                   @PathVariable @PositiveOrZero int index, @RequestPart("file") MultipartFile file) {
        return service.upload(id, version, index, file);
    }
    @DeleteMapping("/{index}")
    public AdminProductDto remove(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long version,
                                  @PathVariable @PositiveOrZero int index) { return service.remove(id, version, index); }
}
