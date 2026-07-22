package com.mossyanwar.ordersinvoicing.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.invoice.OverdueInvoiceMarker;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

// own database because the overdue check below touches every invoice
@IntegrationTest
@TestPropertySource(properties = "test.isolated-database=audit")
class AuditLogIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    OverdueInvoiceMarker overdue;

    Api admin;
    Api clerk;
    TestData data;

    @BeforeEach
    void setUp() {
        admin = new Api(rest);
        clerk = admin.as("clerk");
        data = new TestData(admin);
    }

    @Test
    void recordsWhoMovedAnOrderThroughItsLifecycle() {
        long product = data.product("10.00", "STANDARD");
        long customer = data.customer();
        long orderId = clerk.post("/api/orders", Map.of("customerId", customer,
                "lines", List.of(Map.of("productId", product, "quantity", 2)))).getBody().get("id").asLong();
        clerk.post("/api/orders/" + orderId + "/confirm", null);
        clerk.post("/api/orders/" + orderId + "/fulfil", null);
        clerk.post("/api/orders/" + orderId + "/invoice", null);

        JsonNode events = admin.get("/api/audit-events?entityType=ORDER&entityId=" + orderId).getBody().get("content");

        assertThat(events).extracting(e -> e.get("action").asText(), e -> e.path("fromStatus").asText(null),
                        e -> e.get("toStatus").asText(), e -> e.get("actor").asText())
                .containsExactly(
                        tuple("CREATED", null, "DRAFT", "clerk"),
                        tuple("CONFIRMED", "DRAFT", "CONFIRMED", "clerk"),
                        tuple("FULFILLED", "CONFIRMED", "FULFILLED", "clerk"),
                        tuple("INVOICED", "FULFILLED", "INVOICED", "clerk"));
    }

    @Test
    void recordsInvoiceChangesIncludingTheOnesMadeByTheNightlyJob() {
        long customer = data.customer(7);
        JsonNode invoice = data.invoice(customer, data.product("20.00", "ZERO"), 3);   // 60.00
        long invoiceId = invoice.get("id").asLong();
        clerk.post("/api/payments", Map.of("customerId", customer, "amount", "15.00",
                "receivedOn", LocalDate.now().toString(), "method", "CARD"));
        overdue.markOverdue(LocalDate.parse(invoice.get("dueDate").asText()).plusDays(1));

        JsonNode events = admin.get("/api/audit-events?entityType=INVOICE&entityId=" + invoiceId).getBody().get("content");

        assertThat(events).extracting(e -> e.get("action").asText(), e -> e.get("toStatus").asText(), e -> e.get("actor").asText())
                .containsExactly(
                        tuple("ISSUED", "ISSUED", "admin"),
                        tuple("PAYMENT_APPLIED", "PART_PAID", "clerk"),
                        tuple("MARKED_OVERDUE", "OVERDUE", "system"));
        assertThat(events.get(1).get("detail").asText()).isEqualTo("15.00 applied, 45.00 outstanding");
    }

    @Test
    void aRejectedChangeLeavesNoAuditEvent() {
        long orderId = data.order(data.customer(), data.product("1.00", "ZERO"), 1);

        assertThat(clerk.post("/api/orders/" + orderId + "/fulfil", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        JsonNode events = admin.get("/api/audit-events?entityType=ORDER&entityId=" + orderId).getBody().get("content");
        assertThat(events).extracting(e -> e.get("action").asText()).containsExactly("CREATED");
    }

    @Test
    void onlyAdminsCanReadTheAuditLog() {
        assertThat(clerk.get("/api/audit-events").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(admin.as("viewer").get("/api/audit-events").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
