package com.rahul.ms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.rahul.ms.order.dto.ProductResponse;

@FeignClient(name = "product-service", url = "${product-service.url}")
public interface ProductClient {

    @GetMapping ("/api/products/{productId}")
    ProductResponse getProductById(@PathVariable("productId") String productId);

}
