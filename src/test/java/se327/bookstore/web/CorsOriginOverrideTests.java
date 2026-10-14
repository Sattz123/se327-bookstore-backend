package se327.bookstore.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import se327.bookstore.config.WebConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = WebFoundationTests.TestController.class,
        properties = "app.frontend-origin=https://bookstore.example")
@Import({WebConfig.class, WebFoundationTests.TestController.class})
class CorsOriginOverrideTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private WebFoundationTests.FailureService failureService;

    @Test
    void allowsTheConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "https://bookstore.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "https://bookstore.example"));
    }

    @Test
    void doesNotKeepTheDevelopmentOriginWhenOverridden() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
