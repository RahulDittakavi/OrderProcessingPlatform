package com.rahul.ms.product.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.rahul.ms.product.dto.ProductRequest;
import com.rahul.ms.product.dto.ProductResponse;
import com.rahul.ms.product.model.Product;
import com.rahul.ms.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.rahul.ms.product.exception.ProductNotFoundException;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    
    public ProductResponse createProduct(ProductRequest productRequest) {
        Product product = Product.builder()
                .skuCode(productRequest.skuCode())
                .name(productRequest.name())
                .description(productRequest.description())
                .price(productRequest.price())
                .build();
        productRepository.save(product);
        log.info("Product {} is saved", product.getId());
        return new ProductResponse(
                product.getId(),
                product.getSkuCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
        );
    }

    public List<ProductResponse> getAllProducts() {
        List<ProductResponse> products = productRepository.findAll()
                  .stream()
                  .map(product -> new ProductResponse(
                          product.getId(),
                          product.getSkuCode(),
                          product.getName(),
                          product.getDescription(),
                          product.getPrice()
                  ))
                  .collect(Collectors.toList());
        log.debug("Retrieved {} products", products.size());
        return products;
    }

    public ProductResponse getProductById(String id) {
        Product product = productRepository.findById(id)
                                                .orElseThrow(() -> {
                                                    log.warn("Product not found productId={}", id);
                                                    return new ProductNotFoundException("Product not found: " + id);
                                                });
        log.debug("Retrieved product productId={}", id);
        return new ProductResponse(
                product.getId(),
                product.getSkuCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
        );
    }

    public ProductResponse updateProduct(String id, ProductRequest productRequest) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Cannot update missing product productId={}", id);
                    return new ProductNotFoundException("Product not found: " + id);
                });

        product.setSkuCode(productRequest.skuCode());
        product.setName(productRequest.name());
        product.setDescription(productRequest.description());
        product.setPrice(productRequest.price());

        productRepository.save(product);
        log.info("Product {} is updated", product.getId());

        return new ProductResponse(
                product.getId(),
                product.getSkuCode(),
                product.getName(),
                product.getDescription(),
                product.getPrice()
        );
    }

    public void deleteProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Cannot delete missing product productId={}", id);
                    return new ProductNotFoundException("Product not found");
                });

        productRepository.delete(product);
        log.info("Product {} is deleted", product.getId());
    }

    public Optional<ProductResponse> getProductFallback(String productId, Throwable throwable) {
        log.warn("Product lookup fallback returned no product productId={}", productId, throwable);
        return Optional.empty();
    }
}