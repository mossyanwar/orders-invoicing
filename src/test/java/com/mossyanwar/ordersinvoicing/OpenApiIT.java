package com.mossyanwar.ordersinvoicing;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OpenApiIT {

    @Autowired
    TestRestTemplate rest;

    @Test
    void publishesTheApiDescriptionWithoutLogin() {
        ResponseEntity<JsonNode> response = new Api(rest).anonymous().get("/v3/api-docs");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode doc = response.getBody();
        assertThat(doc.get("components").get("securitySchemes").has("basicAuth")).isTrue();
        assertThat(doc.get("paths").has("/api/orders/{id}/confirm")).isTrue();
        assertThat(doc.get("paths").get("/api/payments").get("post").get("parameters").findValuesAsText("name"))
                .contains("Idempotency-Key");
    }
}
