package com.mossyanwar.ordersinvoicing.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class InvoiceApiIT {

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
    void issuesAnInvoiceForAFulfilledOrder() {
        long customer = data.customer(14);
        long orderId = data.fulfilledOrder(customer, data.product("19.99", "STANDARD"), 7);

        ResponseEntity<JsonNode> response = api.post("/api/orders/" + orderId + "/invoice", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode invoice = response.getBody();
        assertThat(response.getHeaders().getLocation()).hasPath("/api/invoices/" + invoice.get("id").asLong());
        assertThat(invoice.get("invoiceNumber").asText()).matches("INV-\\d{4}-\\d{6}");
        assertThat(invoice.get("status").asText()).isEqualTo("ISSUED");
        assertThat(invoice.get("netTotal").decimalValue()).isEqualByComparingTo("139.93");
        assertThat(invoice.get("vatTotal").decimalValue()).isEqualByComparingTo("27.99");
        assertThat(invoice.get("grossTotal").decimalValue()).isEqualByComparingTo("167.92");
        assertThat(invoice.get("outstanding").decimalValue()).isEqualByComparingTo("167.92");
        LocalDate issued = LocalDate.parse(invoice.get("issueDate").asText());
        assertThat(LocalDate.parse(invoice.get("dueDate").asText())).isEqualTo(issued.plusDays(14));
        assertThat(invoice.get("vatBreakdown").get(0).get("rate").asText()).isEqualTo("STANDARD");

        assertThat(api.get("/api/orders/" + orderId).getBody().get("status").asText()).isEqualTo("INVOICED");
    }

    @Test
    void anOrderIsInvoicedOnlyOnce() {
        long orderId = data.fulfilledOrder(data.customer(), data.product("5.00", "ZERO"), 1);
        api.post("/api/orders/" + orderId + "/invoice", null);

        ResponseEntity<JsonNode> second = api.post("/api/orders/" + orderId + "/invoice", null);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(second.getBody().get("detail").asText()).contains("already been invoiced");
    }

    @Test
    void anUnfulfilledOrderCannotBeInvoiced() {
        long orderId = data.order(data.customer(), data.product("5.00", "ZERO"), 1);
        api.post("/api/orders/" + orderId + "/confirm", null);

        ResponseEntity<JsonNode> response = api.post("/api/orders/" + orderId + "/invoice", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("type").asText()).endsWith("/invalid-state-transition");
        assertThat(api.get("/api/orders/" + orderId).getBody().get("status").asText()).isEqualTo("CONFIRMED");
    }

    @Test
    void listsInvoicesForACustomer() {
        long customer = data.customer();
        long product = data.product("2.50", "REDUCED");
        api.post("/api/orders/" + data.fulfilledOrder(customer, product, 1) + "/invoice", null);
        api.post("/api/orders/" + data.fulfilledOrder(customer, product, 2) + "/invoice", null);

        JsonNode page = api.get("/api/invoices?customerId=" + customer + "&status=ISSUED").getBody();

        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("content").findValuesAsText("customerId")).containsOnly(String.valueOf(customer));
    }
}
