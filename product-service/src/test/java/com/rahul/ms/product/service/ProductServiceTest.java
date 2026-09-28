package com.rahul.ms.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import java.math.BigDecimal;
import java.util.Optional;

import com.rahul.ms.product.dto.ProductResponse;
import com.rahul.ms.product.model.Product;
import com.rahul.ms.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    public void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    public void testGetProductById() {
        String productId = "123";
        Product product = Product.builder()
                .id(productId)
            .skuCode("sku-test-123")
                .name("Test Product")
                .description("This is a test product")
                .price(new BigDecimal("9.99"))
                .build();

        given(productRepository.findById(productId)).willReturn(Optional.of(product));

        ProductResponse result = productService.getProductById(productId);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(productId);
        assertThat(result.skuCode()).isEqualTo("sku-test-123");
        assertThat(result.name()).isEqualTo("Test Product");
        assertThat(result.description()).isEqualTo("This is a test product");
        assertThat(result.price()).isEqualTo(new BigDecimal("9.99"));

        verify(productRepository).findById(productId);
    }
}
