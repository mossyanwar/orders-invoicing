package com.mossyanwar.ordersinvoicing.customer;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class CustomerApiIT {

    @Autowired
    TestRestTemplate rest;

    Api clerk;

    @BeforeEach
    void setUp() {
        clerk = new Api(rest).as("clerk");
    }

    @Test
    void createsACustomerWithDefaultTermsAndANormalisedEmail() {
        String email = "Accounts-" + UUID.randomUUID() + "@Example.TEST";

        ResponseEntity<JsonNode> response = clerk.post("/api/customers", Map.of(
                "name", "  Northwind Traders  ", "email", email, "billingAddress", "2 Park Row, Leeds"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("name").asText()).isEqualTo("Northwind Traders");
        assertThat(response.getBody().get("email").asText()).isEqualTo(email.toLowerCase());
        assertThat(response.getBody().get("paymentTermsDays").asInt()).isEqualTo(30);
    }

    @Test
    void emailsAreUniqueIgnoringCase() {
        String email = "dup-" + UUID.randomUUID() + "@example.test";
        clerk.post("/api/customers", body("First", email, 30));

        ResponseEntity<JsonNode> duplicate = clerk.post("/api/customers", body("Second", email.toUpperCase(), 30));

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("detail").asText()).contains("already exists");
    }

    @Test
    void updatesNeedTheCurrentVersion() {
        String email = "upd-" + UUID.randomUUID() + "@example.test";
        long id = clerk.post("/api/customers", body("Old name", email, 30)).getBody().get("id").asLong();
        Map<String, Object> update = body("New name", email, 14);
        update.put("version", 0);

        ResponseEntity<JsonNode> first = clerk.put("/api/customers/" + id, update);
        ResponseEntity<JsonNode> stale = clerk.put("/api/customers/" + id, update);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("paymentTermsDays").asInt()).isEqualTo(14);
        assertThat(first.getBody().get("version").asLong()).isEqualTo(1);
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody().get("type").asText()).endsWith("/concurrent-update");
    }

    @Test
    void paymentTermsAreLimited() {
        ResponseEntity<JsonNode> response = clerk.post("/api/customers",
                body("Slow Payer", "slow-" + UUID.randomUUID() + "@example.test", 365));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").get(0).get("field").asText()).isEqualTo("paymentTermsDays");
    }

    @Test
    void unknownCustomersAreNotFound() {
        assertThat(clerk.get("/api/customers/987654321").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static Map<String, Object> body(String name, String email, int terms) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("billingAddress", "1 Test Street, Leeds");
        body.put("paymentTermsDays", terms);
        return body;
    }
}
