package se327.bookstore.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import se327.bookstore.config.WebConfig;
import se327.bookstore.exception.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.doThrow;

@WebMvcTest(controllers = WebFoundationTests.TestController.class,
        properties = "app.frontend-origin=http://localhost:5173")
@Import({WebConfig.class, GlobalExceptionHandler.class, WebFoundationTests.TestController.class})
class WebFoundationTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private FailureService failureService;

    @Test
    void acceptsValidJson() throws Exception {
        mvc.perform(post("/api/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Book\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Book"));
    }

    @Test
    void returnsFieldValidationErrors() throws Exception {
        mvc.perform(post("/api/test").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/test"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("Name is required."));
    }

    @Test
    void returnsSafeMessageForMalformedJson() throws Exception {
        mvc.perform(post("/api/test").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("The request body is missing or contains invalid JSON."));
    }

    @Test
    void returnsProblemDetailsForInvalidQueryParameter() throws Exception {
        mvc.perform(get("/api/test").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/test"));
    }

    @Test
    void preservesExplicitHttpErrors() throws Exception {
        mvc.perform(get("/api/test/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Book not found."));
    }

    @Test
    void returnsProblemDetailsForUnmappedPath() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        doThrow(new IllegalStateException("Internal database information"))
                .when(failureService).fail();

        mvc.perform(get("/api/test/failure")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail")
                        .value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void allowsFrontendPreflightWithJsonAndAuthorization() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"));
    }

    @Test
    void rejectsOtherOrigins() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "https://another-site.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void includesCorsHeadersOnValidationErrors() throws Exception {
        mvc.perform(post("/api/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"})
    void allowsConfiguredMethods(String method) throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, method))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void rejectsUnconfiguredMethod() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "TRACE"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void rejectsUnconfiguredHeader() throws Exception {
        mvc.perform(options("/api/test")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-Untrusted-Header"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void allowsFrontendActualRequest() throws Exception {
        mvc.perform(get("/api/test").param("page", "1")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                        "http://localhost:5173"));
    }

    @Test
    void rejectsActualRequestFromAnotherOrigin() throws Exception {
        mvc.perform(get("/api/test").param("page", "1")
                        .header(HttpHeaders.ORIGIN, "https://another-site.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void permitsRequestsWithoutOriginHeader() throws Exception {
        mvc.perform(get("/api/test").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("1"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void returnsProblemDetailsForMissingBody() throws Exception {
        mvc.perform(post("/api/test").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("The request body is missing or contains invalid JSON."));
    }

    @Test
    void returnsProblemDetailsForWrongParameterType() throws Exception {
        mvc.perform(get("/api/test").param("page", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsProblemDetailsForUnsupportedContentType() throws Exception {
        mvc.perform(post("/api/test").contentType(MediaType.TEXT_PLAIN).content("Book"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void preservesMethodNotAllowedStatusAndHeader() throws Exception {
        mvc.perform(put("/api/test/missing"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "GET"))
                .andExpect(jsonPath("$.status").value(405));
    }

    // These endpoints exist only in this test's MVC context.
    @RestController
    @RequestMapping("/api/test")
    public static class TestController {

        private final FailureService failureService;

        public TestController(FailureService failureService) {
            this.failureService = failureService;
        }

        @PostMapping
        public TestRequest create(@Valid @RequestBody TestRequest request) {
            return request;
        }

        @GetMapping
        public int list(@RequestParam @Min(1) int page) {
            return page;
        }

        @GetMapping("/missing")
        public void missing() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found.");
        }

        @GetMapping("/failure")
        public void failure() {
            failureService.fail();
        }
    }

    public interface FailureService {
        void fail();
    }

    public record TestRequest(@NotBlank(message = "Name is required.") String name) {
    }
}
