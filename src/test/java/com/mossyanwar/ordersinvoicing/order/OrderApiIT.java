package com.mossyanwar.ordersinvoicing.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OrderApiIT {

    @Autowired
    TestRestTemplate rest;

    Api api;
    TestData data;

    @BeforeEach
    void setUp() {
        api = new Api(rest);
        data = new TestData(api);
    }

    @Test
    void createsADraftOrderWithTotals() {
        long customer = data.customer();
        long widget = data.product("19.99", "STANDARD");
        long book = data.product("8.00", "ZERO");

        ResponseEntity<JsonNode> response = api.post("/api/orders", Map.of(
                "customerId", customer,
                "lines", List.of(
                        Map.of("productId", widget, "quantity", 7),
                        Map.of("productId", book, "quantity", 2))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasPath("/api/orders/" + response.getBody().get("id").asLong());
        JsonNode order = response.getBody();
        assertThat(order.get("status").asText()).isEqualTo("DRAFT");
        assertThat(order.get("lines")).hasSize(2);
        assertThat(order.get("netTotal").decimalValue()).isEqualByComparingTo("155.93");
        assertThat(order.get("vatTotal").decimalValue()).isEqualByComparingTo("27.99");
        assertThat(order.get("grossTotal").decimalValue()).isEqualByComparingTo("183.92");
    }

    @Test
    void walksTheLifecycleAndRejectsIllegalMoves() {
        long orderId = data.order(data.customer(), data.product("10.00", "STANDARD"), 1);

        assertThat(status(api.post("/api/orders/" + orderId + "/confirm", null))).isEqualTo("CONFIRMED");

        ResponseEntity<JsonNode> again = api.post("/api/orders/" + orderId + "/confirm", null);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(again.getBody().get("type").asText()).endsWith("/invalid-state-transition");
        assertThat(again.getBody().get("currentStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(again.getBody().get("requestedStatus").asText()).isEqualTo("CONFIRMED");
        assertThat(again.getBody().get("instance").asText()).isEqualTo("/api/orders/" + orderId + "/confirm");

        assertThat(status(api.post("/api/orders/" + orderId + "/fulfil", null))).isEqualTo("FULFILLED");
        assertThat(api.post("/api/orders/" + orderId + "/cancel", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void linesCanBeEditedOnlyWhileDraft() {
        long product = data.product("5.00", "REDUCED");
        long orderId = data.order(data.customer(), product, 1);

        JsonNode withTwoLines = api.post("/api/orders/" + orderId + "/lines", Map.of("productId", product, "quantity", 4)).getBody();
        assertThat(withTwoLines.get("lines")).hasSize(2);
        long firstLine = withTwoLines.get("lines").get(0).get("id").asLong();

        JsonNode afterRemoval = api.delete("/api/orders/" + orderId + "/lines/" + firstLine).getBody();
        assertThat(afterRemoval.get("lines")).hasSize(1);
        assertThat(afterRemoval.get("netTotal").decimalValue()).isEqualByComparingTo("20.00");

        api.post("/api/orders/" + orderId + "/confirm", null);
        ResponseEntity<JsonNode> locked = api.post("/api/orders/" + orderId + "/lines", Map.of("productId", product, "quantity", 1));
        assertThat(locked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void anEmptyOrderCannotBeConfirmed() {
        long orderId = api.post("/api/orders", Map.of("customerId", data.customer())).getBody().get("id").asLong();

        ResponseEntity<JsonNode> response = api.post("/api/orders/" + orderId + "/confirm", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("detail").asText()).contains("at least one line");
    }

    @Test
    void validationErrorsListTheFields() {
        ResponseEntity<JsonNode> response = api.post("/api/orders", Map.of(
                "lines", List.of(Map.of("productId", 1, "quantity", 0))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("type").asText()).endsWith("/validation");
        assertThat(response.getBody().get("errors").findValuesAsText("field"))
                .containsExactlyInAnyOrder("customerId", "lines[0].quantity");
    }

    @Test
    void unknownCustomerIsNotFound() {
        ResponseEntity<JsonNode> response = api.post("/api/orders", Map.of("customerId", 999_999));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("detail").asText()).isEqualTo("Customer 999999 was not found");
    }

    @Test
    void listsOrdersByStatusAndCustomerWithPaging() {
        long customer = data.customer();
        long product = data.product("1.00", "ZERO");
        data.order(customer, product, 1);
        data.order(customer, product, 2);
        long confirmed = data.order(customer, product, 3);
        api.post("/api/orders/" + confirmed + "/confirm", null);

        JsonNode drafts = api.get("/api/orders?customerId=" + customer + "&status=DRAFT&size=1").getBody();

        assertThat(drafts.get("totalElements").asLong()).isEqualTo(2);
        assertThat(drafts.get("totalPages").asInt()).isEqualTo(2);
        assertThat(drafts.get("content")).hasSize(1);
    }

    @Test
    void staleProductUpdateIsRejectedAndOrdersKeepTheirPrice() {
        long product = data.product("10.00", "STANDARD");
        long orderId = data.order(data.customer(), product, 1);
        Map<String, Object> update = Map.of("name", "Renamed", "unitPrice", "12.00", "vatRate", "STANDARD",
                "active", true, "version", 0);

        assertThat(api.put("/api/products/" + product, update).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> stale = api.put("/api/products/" + product, update);

        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("type").asText()).endsWith("/concurrent-update");
        JsonNode order = api.get("/api/orders/" + orderId).getBody();
        assertThat(order.get("lines").get(0).get("unitPrice").decimalValue()).isEqualByComparingTo("10.00");
    }

    private static String status(ResponseEntity<JsonNode> response) {
        assertThat(response.getStatusCode()).as(String.valueOf(response.getBody())).isEqualTo(HttpStatus.OK);
        return response.getBody().get("status").asText();
    }
}
