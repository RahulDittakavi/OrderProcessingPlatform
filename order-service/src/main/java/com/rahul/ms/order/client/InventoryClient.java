package com.rahul.ms.order.client;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
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

        log.debug("Checking inventory for {} SKU(s)", skuCodes.size());
        try {
            return restClientBuilder.build()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<InventoryResponse>>() {});
        } catch (RestClientException exception) {
            log.error("Inventory lookup failed for {} SKU(s)", skuCodes.size(), exception);
            throw exception;
        }
    }

    public void reduceStock(String reservationId, String skuCode, int quantity) {
        String uri = UriComponentsBuilder.fromUriString(inventoryServiceUrl + "/api/inventory/reduce-stock")
                .queryParam("skuCode", skuCode)
                .queryParam("quantity", quantity)
                .toUriString();

        log.debug("Requesting inventory reservation skuCode={} quantity={}", skuCode, quantity);
        try {
            restClientBuilder.build()
                    .put()
                    .uri(uri)
                    .header("Idempotency-Key", reservationId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                log.error("Inventory reservation returned server error skuCode={} quantity={} status={}",
                        skuCode, quantity, exception.getStatusCode(), exception);
            }
            throw exception;
        } catch (RestClientException exception) {
            log.error("Inventory reservation request failed skuCode={} quantity={}", skuCode, quantity, exception);
            throw exception;
        }
    }
}