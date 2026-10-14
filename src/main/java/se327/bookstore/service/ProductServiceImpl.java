package se327.bookstore.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.dao.ProductDao;
import se327.bookstore.dto.CategoryDto;
import se327.bookstore.dto.ProductDto;
import se327.bookstore.dto.ProductPageDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import se327.bookstore.entity.Product;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductDao productDao;
    private final CategoryService categoryService;
    private static final int PAGE_SIZE = 15;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    @Override
    public ProductPageDto getProducts(String query, Long categoryId, int page) {
        if (categoryId != null) categoryService.getCategory(categoryId);
        var results = productDao.findVisibleProducts(query.strip(), categoryId,
                PageRequest.of(page - 1, PAGE_SIZE, NEWEST_FIRST));
        return new ProductPageDto(results.stream().map(this::toDto).toList(), page, PAGE_SIZE,
                results.getTotalElements(), results.getTotalPages());
    }

    @Override
    public List<ProductDto> getFeaturedProducts() {
        return productDao.findVisibleProducts("", null, PageRequest.of(0, 8, NEWEST_FIRST))
                .stream().map(this::toDto).toList();
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
