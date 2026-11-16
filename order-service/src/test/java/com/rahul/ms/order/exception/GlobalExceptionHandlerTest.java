package com.rahul.ms.order.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    void mapsMissingProductToNotFound() {
        var response = exceptionHandler.handleProductNotFound(new ProductNotFoundException("Product not found"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Product not found", response.getBody().get("error"));
    }

    @Test
    void mapsProductServiceFailureToServiceUnavailable() {
        var response = exceptionHandler.handleProductServiceUnavailable(
                new ProductServiceUnavailableException("Product service is unavailable", new RuntimeException()));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("Product service is unavailable", response.getBody().get("error"));
    }
}
