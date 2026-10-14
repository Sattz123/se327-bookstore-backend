package se327.bookstore.controller;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import se327.bookstore.dto.ProductDto;
import se327.bookstore.dto.ProductPageDto;
import se327.bookstore.service.ProductService;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ProductPageDto getProducts(
            @RequestParam(defaultValue = "") @Size(max = 100) String q,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(defaultValue = "1") @Min(1) @Max(1000000) int page) {
        return productService.getProducts(q, categoryId, page);
    }

    @GetMapping("/featured")
    public List<ProductDto> getFeaturedProducts() {
        return productService.getFeaturedProducts();
    }

    @GetMapping("/{id}")
    public ProductDto getProduct(@PathVariable @Positive Long id) {
        return productService.getProduct(id);
    }
}
