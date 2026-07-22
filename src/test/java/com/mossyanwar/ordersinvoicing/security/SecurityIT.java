package com.mossyanwar.ordersinvoicing.security;

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
class SecurityIT {

    @Autowired
    TestRestTemplate rest;

    Api admin;
    TestData data;

    @BeforeEach
    void setUp() {
        admin = new Api(rest);
        data = new TestData(admin);
    }

    @Test
    void anonymousCallersGetA401ProblemWithABasicChallenge() {
        ResponseEntity<JsonNode> response = admin.anonymous().get("/api/orders");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).startsWith("Basic");
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Authentication required");
    }

    @Test
    void aWrongPasswordIsRejected() {
        ResponseEntity<JsonNode> response = rest.withBasicAuth("clerk", "not-the-password")
                .getForEntity("/api/orders", JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void viewersCanReadButNotWrite() {
        Api viewer = admin.as("viewer");
        long customer = data.customer();

        assertThat(viewer.get("/api/customers/" + customer).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(viewer.get("/api/invoices").getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> write = viewer.post("/api/orders", Map.of("customerId", customer));
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(write.getBody().get("type").asText()).endsWith("/forbidden");
    }

    @Test
    void clerksRunTheOrderToCashFlowButCannotChangeTheCatalogueOrIssueCredits() {
        Api clerk = admin.as("clerk");
        long product = data.product("10.00", "STANDARD");
        long customer = data.customer();

        ResponseEntity<JsonNode> order = clerk.post("/api/orders", Map.of(
                "customerId", customer, "lines", List.of(Map.of("productId", product, "quantity", 1))));
        assertThat(order.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long orderId = order.getBody().get("id").asLong();
        assertThat(clerk.post("/api/orders/" + orderId + "/confirm", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clerk.post("/api/orders/" + orderId + "/fulfil", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> invoice = clerk.post("/api/orders/" + orderId + "/invoice", null);
        assertThat(invoice.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(clerk.post("/api/products", Map.of("sku", "NOPE-1", "name", "x", "unitPrice", "1.00", "vatRate", "ZERO"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(clerk.put("/api/products/" + product, Map.of("name", "x", "unitPrice", "1.00", "vatRate", "ZERO",
                "active", true, "version", 0)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(clerk.post("/api/invoices/" + invoice.getBody().get("id").asLong() + "/credit-notes",
                Map.of("reason", "x")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(clerk.post("/api/admin/jobs/mark-overdue", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void adminsCanDoEverything() {
        assertThat(admin.post("/api/admin/jobs/mark-overdue", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.get("/api/orders").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void healthIsPublicButItsDetailsAreNot() {
        ResponseEntity<JsonNode> anonymous = admin.anonymous().get("/actuator/health");
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(anonymous.getBody().get("status").asText()).isEqualTo("UP");
        assertThat(anonymous.getBody().has("components")).isFalse();

        ResponseEntity<JsonNode> asAdmin = admin.get("/actuator/health");
        assertThat(asAdmin.getBody().get("components").has("db")).isTrue();
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() {
        assertThat(admin.get("/actuator/env").getStatusCode()).isIn(HttpStatus.NOT_FOUND, HttpStatus.FORBIDDEN);
    }
}
