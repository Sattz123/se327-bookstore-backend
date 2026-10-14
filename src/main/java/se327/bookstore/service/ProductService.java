package se327.bookstore.service;

import se327.bookstore.dto.ProductDto;

import java.util.List;

public interface ProductService {
    List<ProductDto> getProducts();

    ProductDto getProduct(Long id);
}
