package se327.bookstore.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import se327.bookstore.dto.*;
import se327.bookstore.service.AdminCategoryService;
import java.util.List;

@RestController @RequestMapping("/api/admin/categories") @RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {
    private final AdminCategoryService service;
    @GetMapping public List<AdminCategoryDetailDto> list(@RequestParam(defaultValue = "") @Size(max = 100) String q) { return service.list(q); }
    @GetMapping("/{id}") public AdminCategoryDetailDto get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public AdminCategoryDetailDto create(@Valid @RequestPart("category") AdminCategoryRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) { return service.save(null, request, file); }
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminCategoryDetailDto update(@PathVariable @Positive Long id, @Valid @RequestPart("category") AdminCategoryRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file) { return service.save(id, request, file); }
    @PatchMapping("/{id}/visibility")
    public AdminCategoryDetailDto visibility(@PathVariable @Positive Long id, @Valid @RequestBody AdminVisibilityRequest request) { return service.visibility(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long version) { service.delete(id, version); }
}
