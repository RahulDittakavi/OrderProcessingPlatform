package com.rahul.ms.order.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import com.rahul.ms.order.exception.ProductServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

class ProductClientTest {

    private final RestClient.Builder restClientBuilder = mock(RestClient.Builder.class);
    private final ProductClient productClient;

    ProductClientTest() {
        when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        when(restClientBuilder.build()).thenReturn(mock(RestClient.class));
        productClient = new ProductClient(restClientBuilder, "http://product-service");
    }

    @Test
    void fallbackReturnsEmptyForProductNotFound() {
        var notFound = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        assertEquals(Optional.empty(), productClient.getProductFallback("missing-product", notFound));
    }

    @Test
    void fallbackThrowsServiceUnavailableForOtherFailures() {
        var outage = new IllegalStateException("connection failed");

        var exception = assertThrows(ProductServiceUnavailableException.class,
                () -> productClient.getProductFallback("product-123", outage));

        assertEquals(outage, exception.getCause());
    }
}
