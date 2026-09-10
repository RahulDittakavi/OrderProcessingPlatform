package com.rahul.ms.order.client;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.rahul.ms.order.dto.InventoryResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryClient {

    private final RestClient.Builder restClientBuilder;

    @Value("${inventory.service.url:http://inventory-service}")
    private String inventoryServiceUrl;

    public List<InventoryResponse> checkStock(List<String> skuCodes) {
        String uri = UriComponentsBuilder.fromUriString(inventoryServiceUrl + "/api/inventory")
                .queryParam("skuCode", skuCodes)
                .toUriString();

        return restClientBuilder.build()
                .get()
                .uri(uri)
                .retrieve()
                .body(new ParameterizedTypeReference<List<InventoryResponse>>() {});
    }
}