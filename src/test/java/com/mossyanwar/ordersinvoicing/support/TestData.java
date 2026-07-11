package com.mossyanwar.ordersinvoicing.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Creates customers, products and orders through the API. Every call uses unique emails and SKUs. */
public class TestData {

    private final Api api;

    public TestData(Api api) {
        this.api = api;
    }

    public long customer() {
        return customer(30);
    }

    public long customer(int paymentTermsDays) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<JsonNode> response = api.post("/api/customers", Map.of(
                "name", "Customer " + suffix,
                "email", "accounts-" + suffix + "@example.test",
                "billingAddress", "1 Test Street, Leeds LS1 1AA",
                "paymentTermsDays", paymentTermsDays));
        return created(response);
    }

    public long product(String unitPrice, String vatRate) {
        String sku = "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ResponseEntity<JsonNode> response = api.post("/api/products", Map.of(
                "sku", sku,
                "name", "Product " + sku,
                "unitPrice", unitPrice,
                "vatRate", vatRate));
        return created(response);
    }

    public long order(long customerId, long productId, int quantity) {
        ResponseEntity<JsonNode> response = api.post("/api/orders", Map.of(
                "customerId", customerId,
                "lines", List.of(Map.of("productId", productId, "quantity", quantity))));
        return created(response);
    }

    public long fulfilledOrder(long customerId, long productId, int quantity) {
        long orderId = order(customerId, productId, quantity);
        assertThat(api.post("/api/orders/" + orderId + "/confirm", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(api.post("/api/orders/" + orderId + "/fulfil", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        return orderId;
    }

    private static long created(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).as(String.valueOf(response.getBody())).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id").asLong();
    }
}
