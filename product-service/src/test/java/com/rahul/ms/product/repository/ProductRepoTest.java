package com.rahul.ms.product.repository;
import com.rahul.ms.product.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
public class ProductRepoTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    public void testSaveProduct() {
        Product product = new Product();
        product.setName("Test Product");
        product.setDescription("This is a test product");
        product.setPrice(new BigDecimal("9.99"));

        Product savedProduct = productRepository.save(product);

        assertNotNull(savedProduct.getId());
        assertEquals("Test Product", savedProduct.getName());
        assertEquals("This is a test product", savedProduct.getDescription());
        assertEquals(new BigDecimal("9.99"), savedProduct.getPrice());
    }


}
