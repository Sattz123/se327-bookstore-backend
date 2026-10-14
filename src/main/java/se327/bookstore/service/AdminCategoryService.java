package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.CategoryDao;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.AdminCategoryDetailDto;
import se327.bookstore.dto.AdminCategoryRequest;
import se327.bookstore.dto.AdminVisibilityRequest;
import se327.bookstore.entity.Category;
import java.net.URI;
import java.util.List;
import java.util.Locale;

@Service @RequiredArgsConstructor @Transactional
public class AdminCategoryService {
    private final CategoryDao categories;
    private final ProductDao products;
    private final ProductImageStorage images;

    @Transactional(readOnly = true)
    public List<AdminCategoryDetailDto> list(String query) {
        String search = query.strip().toLowerCase(Locale.ROOT);
        return categories.findAll().stream().filter(c -> c.getName().toLowerCase(Locale.ROOT).contains(search)).map(this::toDto).toList();
    }
    @Transactional(readOnly = true)
    public AdminCategoryDetailDto get(Long id) { return toDto(categories.findById(id).orElseThrow(this::notFound)); }
    public AdminCategoryDetailDto save(Long id, AdminCategoryRequest request, MultipartFile file) {
        Category category = id == null ? Category.builder().build() : lock(id, request.version());
        category.setName(request.name().strip());
        category.setVisible(request.visible());
        String imageUrl = validateImageUrl(request.imageUrl());
        if (file != null) {
            imageUrl = images.store(file);
            String uploaded = imageUrl;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) images.deleteManaged(uploaded); }
            });
        }
        category.setImageUrl(imageUrl);
        return toDto(categories.save(category));
    }
    public AdminCategoryDetailDto visibility(Long id, AdminVisibilityRequest request) {
        Category category = lock(id, request.version());
        category.setVisible(request.visible());
        return toDto(categories.save(category));
    }
    public void delete(Long id, Long version) {
        Category category = lock(id, version);
        long count = products.countByCategoryId(id);
        if (count > 0) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This category contains " + count + " product(s). Reassign them in Products and inventory before deleting it.");
        categories.delete(category);
    }
    private Category lock(Long id, Long version) {
        if (version == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A category version is required. Reload the category.");
        Category category = categories.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (category.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "This category changed. Reload before saving.");
        return category;
    }
    private String validateImageUrl(String value) {
        if (value == null || value.isBlank()) return null;
        String url = value.strip();
        if (url.startsWith("/images/") && !url.contains("..") && !url.contains("\\")) return url;
        try {
            URI uri = URI.create(url);
            if (("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) && uri.getHost() != null) return url;
        } catch (IllegalArgumentException ignored) { }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image URL must be an /images/ path or an HTTP(S) URL.");
    }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found."); }
    private AdminCategoryDetailDto toDto(Category c) {
        return new AdminCategoryDetailDto(c.getId(), c.getName(), c.getImageUrl(), c.isVisible(), products.countByCategoryId(c.getId()), c.getVersion(), c.getCreatedAt());
    }
}
