package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.AdminProductDto;
import se327.bookstore.entity.Product;

@Service @RequiredArgsConstructor @Transactional
public class ProductImageService {
    private final ProductDao products;
    private final ProductImageStorage storage;
    private final AdminProductService adminProducts;

    public AdminProductDto upload(Long id, Long version, Integer index, MultipartFile file) {
        Product product = lock(id, version);
        if (index == null && product.getImages().size() >= 5) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A book can have up to five images.");
        if (index != null) validateIndex(product, index);
        String url = storage.store(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) storage.deleteManaged(url); }
        });
        if (index == null) product.getImages().add(url);
        else removeAfterCommit(product.getImages().set(index, url));
        products.save(product);
        return adminProducts.get(id);
    }
    public AdminProductDto remove(Long id, Long version, int index) {
        Product product = lock(id, version);
        validateIndex(product, index);
        removeAfterCommit(product.getImages().remove(index));
        products.save(product);
        return adminProducts.get(id);
    }
    private Product lock(Long id, Long version) {
        Product product = products.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found."));
        if (product.getVersion() != version) throw new ResponseStatusException(HttpStatus.CONFLICT, "This product changed. Reload before updating its images.");
        return product;
    }
    private void validateIndex(Product product, int index) {
        if (index < 0 || index >= product.getImages().size()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Image not found.");
    }
    private void removeAfterCommit(String url) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.deleteManaged(url); }
        });
    }
}
