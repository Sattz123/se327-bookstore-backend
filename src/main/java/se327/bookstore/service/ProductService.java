package se327.bookstore.service;

import se327.bookstore.dto.ProductDto;
import se327.bookstore.dto.ProductPageDto;

import java.util.List;

public interface ProductService {
    ProductPageDto getProducts(String query, Long categoryId, int page);

    List<ProductDto> getFeaturedProducts();

    ProductDto getProduct(Long id);
}
