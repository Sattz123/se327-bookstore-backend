package se327.bookstore.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import se327.bookstore.controller.ProductController;
import se327.bookstore.service.ProductService;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerFailureTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void returnsASafeErrorWhenTheCatalogFails() throws Exception {
        when(productService.getProducts()).thenThrow(new IllegalStateException("Private database information"));

        mvc.perform(get("/api/products"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."));
    }

    @Test
    void returnsASafeErrorWhenTheBookLookupFails() throws Exception {
        when(productService.getProduct(1L)).thenThrow(new IllegalStateException("Private database information"));

        mvc.perform(get("/api/products/1"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."));
    }
}
