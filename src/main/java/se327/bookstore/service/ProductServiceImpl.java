package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.CategoryDto;
import se327.bookstore.dto.ProductDto;
import se327.bookstore.entity.Product;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductDao productDao;

    @Override
    public List<ProductDto> getProducts() {
        return productDao.findVisibleProducts().stream().map(this::toDto).toList();
    }

    @Override
    public ProductDto getProduct(Long id) {
        Product product = productDao.findVisibleProductById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found."));
        return toDto(product);
    }

    private ProductDto toDto(Product product) {
        var category = product.getCategory();
        return new ProductDto(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getAuthor(), product.getStockQuantity(),
                new CategoryDto(category.getId(), category.getName(), category.getImageUrl()),
                List.copyOf(product.getImages()), product.getFormats().stream().sorted().toList());
    }
}
