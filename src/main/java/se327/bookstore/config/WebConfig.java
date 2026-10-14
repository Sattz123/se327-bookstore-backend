package se327.bookstore.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.http.CacheControl;
import se327.bookstore.service.ProductImageStorage;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendOrigin;
    private final ProductImageStorage imageStorage;

    public WebConfig(@Value("${app.frontend-origin}") String frontendOrigin, ProductImageStorage imageStorage) {
        this.frontendOrigin = frontendOrigin;
        this.imageStorage = imageStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ProductImageStorage.URL_PREFIX + "**")
                .addResourceLocations(imageStorage.resourceLocation())
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization")
                .maxAge(3600);
    }
}
