package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.CategoryDao;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.*;
import se327.bookstore.entity.Product;
import java.util.List;

@Service @RequiredArgsConstructor @Transactional
public class AdminProductService {
    private final ProductDao products;
    private final CategoryDao categories;

    @Transactional(readOnly = true)
    public AdminProductPageDto list(String query, Long categoryId, Boolean visible, int page) {
        var result = products.findAdminProducts(query.strip(), categoryId, visible,
                PageRequest.of(page - 1, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new AdminProductPageDto(result.stream().map(this::toDto).toList(), page, 20, result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public AdminProductDto get(Long id) { return toDto(products.findById(id).orElseThrow(this::notFound)); }
    @Transactional(readOnly = true)
    public List<AdminCategoryDto> categories() {
        return categories.findAll().stream().map(c -> new AdminCategoryDto(c.getId(), c.getName(), c.isVisible())).toList();
    }
    public AdminProductDto create(AdminProductRequest request) {
        Product product = Product.builder().build();
        apply(product, request);
        return toDto(products.save(product));
    }
    public AdminProductDto update(Long id, AdminProductRequest request) {
        Product product = lock(id, request.version());
        apply(product, request);
        return toDto(products.save(product));
    }
    public AdminProductDto inventory(Long id, AdminInventoryRequest request) {
        Product product = lock(id, request.version());
        product.setStockQuantity(request.stockQuantity());
        return toDto(products.save(product));
    }
    public AdminProductDto visibility(Long id, AdminVisibilityRequest request) {
        Product product = lock(id, request.version());
        product.setVisible(request.visible());
        return toDto(products.save(product));
    }
    public AdminProductDto archive(Long id, Long version) {
        Product product = lock(id, version);
        product.setVisible(false);
        return toDto(products.save(product));
    }
    private Product lock(Long id, Long version) {
        if (version == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product version is required. Reload the product.");
        Product product = products.findByIdForUpdate(id).orElseThrow(this::notFound);
        if (product.getVersion() != version)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This product changed since you opened it. Reload before saving.");
        return product;
    }
    private void apply(Product product, AdminProductRequest request) {
        product.setCategory(categories.findById(request.categoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an existing category.")));
        product.setName(request.name().strip());
        product.setDescription(optional(request.description()));
        product.setAuthor(optional(request.author()));
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
        product.setVisible(request.visible());
        product.getFormats().clear();
        product.getFormats().addAll(request.formats());
    }
    private String optional(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."); }
    private AdminProductDto toDto(Product product) {
        var category = product.getCategory();
        return new AdminProductDto(product.getId(), product.getName(), product.getDescription(), product.getAuthor(), product.getPrice(),
                product.getStockQuantity(), product.isVisible(), new AdminCategoryDto(category.getId(), category.getName(), category.isVisible()),
                product.getFormats().stream().sorted().toList(), List.copyOf(product.getImages()), product.getVersion(), product.getUpdatedAt());
    }
}
