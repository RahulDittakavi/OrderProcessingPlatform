package com.rahul.ms.product.controller;

import java.util.List;

import com.rahul.ms.product.dto.ProductRequest;
import com.rahul.ms.product.dto.ProductResponse;
import com.rahul.ms.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "Endpoints for creating, retrieving, updating, and deleting products")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a product", description = "Adds a new product to MongoDB")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Product created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid product request payload")
    })
    public ProductResponse createProduct(@RequestBody ProductRequest productRequest) {
        return productService.createProduct(productRequest);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get all products", description = "Retrieves a list of all products in the database")
    @ApiResponse(responseCode = "200", description = "List of products retrieved successfully")
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Get product by ID", description = "Retrieves details of a specific product by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ProductResponse getProductById(
            @Parameter(description = "Unique identifier of the product", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id) {
        return productService.getProductById(id);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "Update product", description = "Updates an existing product's details by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Product updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid update payload"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public ProductResponse updateProduct(
            @Parameter(description = "Unique identifier of the product to update", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id,
            @RequestBody ProductRequest productRequest) {
        return productService.updateProduct(id, productRequest);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete product", description = "Removes a product from the database by ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Product deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Product not found")
    })
    public void deleteProduct(
            @Parameter(description = "Unique identifier of the product to delete", example = "64f1a2b3c4d5e6f7a8b9c0d1")
            @PathVariable String id) {
        productService.deleteProduct(id);
    }
}