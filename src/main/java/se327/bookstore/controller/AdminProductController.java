package se327.bookstore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import se327.bookstore.dto.*;
import se327.bookstore.service.AdminProductService;
import java.util.List;

@RestController @RequestMapping("/api/admin/products") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminProductController {
    private final AdminProductService service;
    @GetMapping
    public AdminProductPageDto list(@RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) @Positive Long categoryId, @RequestParam(required = false) Boolean visible,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page) {
        return service.list(q, categoryId, visible, page);
    }
    @GetMapping("/categories") public List<AdminCategoryDto> categories() { return service.categories(); }
    @GetMapping("/{id}") public AdminProductDto get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public AdminProductDto create(@Valid @RequestBody AdminProductRequest request) { return service.create(request); }
    @PutMapping("/{id}")
    public AdminProductDto update(@PathVariable @Positive Long id, @Valid @RequestBody AdminProductRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/inventory")
    public AdminProductDto inventory(@PathVariable @Positive Long id, @Valid @RequestBody AdminInventoryRequest request) { return service.inventory(id, request); }
    @PatchMapping("/{id}/visibility")
    public AdminProductDto visibility(@PathVariable @Positive Long id, @Valid @RequestBody AdminVisibilityRequest request) { return service.visibility(id, request); }
    @DeleteMapping("/{id}")
    public AdminProductDto archive(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long version) { return service.archive(id, version); }
}
