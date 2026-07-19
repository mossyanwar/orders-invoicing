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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class CreditNoteApiIT {

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
    void partialThenFullCreditVoidsTheInvoice() {
        JsonNode invoice = issueInvoice("10.00", "STANDARD", 3);
        long invoiceId = invoice.get("id").asLong();
        long lineId = invoice.get("lines").get(0).get("id").asLong();

        ResponseEntity<JsonNode> partial = api.post("/api/invoices/" + invoiceId + "/credit-notes", Map.of(
                "reason", "one unit damaged in transit",
                "lines", List.of(Map.of("invoiceLineId", lineId, "quantity", 1))));

        assertThat(partial.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(partial.getBody().get("creditNoteNumber").asText()).matches("CN-\\d{4}-\\d{6}");
        assertThat(partial.getBody().get("grossTotal").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(partial.getBody().get("invoiceOutstanding").decimalValue()).isEqualByComparingTo("24.00");
        assertThat(api.get(partial.getHeaders().getLocation().getPath()).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> rest = api.post("/api/invoices/" + invoiceId + "/credit-notes",
                Map.of("reason", "customer cancelled the remainder"));

        assertThat(rest.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(rest.getBody().get("grossTotal").decimalValue()).isEqualByComparingTo("24.00");
        assertThat(rest.getBody().get("invoiceStatus").asText()).isEqualTo("VOID");

        JsonNode after = api.get("/api/invoices/" + invoiceId).getBody();
        assertThat(after.get("status").asText()).isEqualTo("VOID");
        assertThat(after.get("amountCredited").decimalValue()).isEqualByComparingTo("36.00");
        assertThat(after.get("outstanding").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(after.get("creditNotes")).hasSize(2);
        assertThat(after.get("lines").get(0).get("creditedQuantity").asInt()).isEqualTo(3);
    }

    @Test
    void overCreditingALineIsRejected() {
        JsonNode invoice = issueInvoice("4.00", "REDUCED", 2);
        long lineId = invoice.get("lines").get(0).get("id").asLong();

        ResponseEntity<JsonNode> response = api.post("/api/invoices/" + invoice.get("id").asLong() + "/credit-notes", Map.of(
                "reason", "too many",
                "lines", List.of(Map.of("invoiceLineId", lineId, "quantity", 3))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("type").asText()).endsWith("/business-rule");
    }

    @Test
    void aReasonIsRequired() {
        JsonNode invoice = issueInvoice("4.00", "REDUCED", 1);

        ResponseEntity<JsonNode> response = api.post("/api/invoices/" + invoice.get("id").asLong() + "/credit-notes", Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").findValuesAsText("field")).containsExactly("reason");
    }

    private JsonNode issueInvoice(String price, String vatRate, int quantity) {
        long orderId = data.fulfilledOrder(data.customer(), data.product(price, vatRate), quantity);
        ResponseEntity<JsonNode> response = api.post("/api/orders/" + orderId + "/invoice", null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }
}
