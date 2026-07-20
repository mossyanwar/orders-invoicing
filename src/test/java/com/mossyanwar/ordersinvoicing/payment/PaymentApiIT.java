package com.mossyanwar.ordersinvoicing.payment;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PaymentApiIT {

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
    void withoutAllocationsAPaymentClearsTheOldestInvoiceFirst() {
        long customer = data.customer();
        long product = data.product("10.00", "STANDARD");
        JsonNode first = data.invoice(customer, product, 3);    // 36.00
        JsonNode second = data.invoice(customer, product, 2);   // 24.00

        ResponseEntity<JsonNode> response = api.post("/api/payments", payment(customer, "50.00", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode allocations = response.getBody().get("allocations");
        assertThat(allocations).hasSize(2);
        assertThat(allocations.get(0).get("invoiceNumber").asText()).isEqualTo(first.get("invoiceNumber").asText());
        assertThat(allocations.get(0).get("amount").decimalValue()).isEqualByComparingTo("36.00");
        assertThat(allocations.get(1).get("amount").decimalValue()).isEqualByComparingTo("14.00");

        assertThat(invoice(first).get("status").asText()).isEqualTo("PAID");
        JsonNode secondAfter = invoice(second);
        assertThat(secondAfter.get("status").asText()).isEqualTo("PART_PAID");
        assertThat(secondAfter.get("outstanding").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void anOverpaymentIsRejectedAndNothingIsRecorded() {
        long customer = data.customer();
        JsonNode invoice = data.invoice(customer, data.product("10.00", "ZERO"), 1);

        ResponseEntity<JsonNode> response = api.post("/api/payments", payment(customer, "10.01", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("detail").asText())
                .isEqualTo("Payment of 10.01 is more than the 10.00 owed across the customer's open invoices");
        assertThat(invoice(invoice).get("amountPaid").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(api.get("/api/payments?customerId=" + customer).getBody().get("totalElements").asLong()).isZero();
    }

    @Test
    void explicitAllocationsMustFitEachInvoice() {
        long customer = data.customer();
        long product = data.product("10.00", "ZERO");
        JsonNode a = data.invoice(customer, product, 1);
        JsonNode b = data.invoice(customer, product, 5);

        ResponseEntity<JsonNode> tooMuchOnA = api.post("/api/payments", payment(customer, "20.00", List.of(
                Map.of("invoiceId", a.get("id").asLong(), "amount", "15.00"),
                Map.of("invoiceId", b.get("id").asLong(), "amount", "5.00"))));
        assertThat(tooMuchOnA.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(tooMuchOnA.getBody().get("detail").asText()).contains("more than the 10.00 outstanding");
        assertThat(invoice(b).get("amountPaid").decimalValue()).as("rolled back").isEqualByComparingTo("0.00");

        ResponseEntity<JsonNode> ok = api.post("/api/payments", payment(customer, "20.00", List.of(
                Map.of("invoiceId", b.get("id").asLong(), "amount", "20.00"))));
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(invoice(a).get("status").asText()).isEqualTo("ISSUED");
        assertThat(invoice(b).get("outstanding").decimalValue()).isEqualByComparingTo("30.00");
    }

    @Test
    void allocationsMustAddUpToThePayment() {
        long customer = data.customer();
        JsonNode invoice = data.invoice(customer, data.product("10.00", "ZERO"), 1);

        ResponseEntity<JsonNode> response = api.post("/api/payments", payment(customer, "10.00", List.of(
                Map.of("invoiceId", invoice.get("id").asLong(), "amount", "4.00"))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("detail").asText()).isEqualTo("Allocations add up to 4.00 but the payment is 10.00");
    }

    @Test
    void cannotPayAnotherCustomersInvoice() {
        long payer = data.customer();
        JsonNode someoneElses = data.invoice(data.customer(), data.product("10.00", "ZERO"), 1);

        ResponseEntity<JsonNode> response = api.post("/api/payments", payment(payer, "10.00", List.of(
                Map.of("invoiceId", someoneElses.get("id").asLong(), "amount", "10.00"))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("detail").asText()).contains("belongs to a different customer");
    }

    @Test
    void aVoidInvoiceTakesNoPayments() {
        long customer = data.customer();
        JsonNode invoice = data.invoice(customer, data.product("10.00", "ZERO"), 1);
        api.post("/api/invoices/" + invoice.get("id").asLong() + "/credit-notes", Map.of("reason", "cancelled"));

        ResponseEntity<JsonNode> response = api.post("/api/payments", payment(customer, "10.00", List.of(
                Map.of("invoiceId", invoice.get("id").asLong(), "amount", "10.00"))));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().get("detail").asText()).contains("is VOID");
    }

    @Test
    void validatesTheRequest() {
        Map<String, Object> body = payment(data.customer(), "10.001", null);
        body.put("receivedOn", LocalDate.now().plusDays(2).toString());

        ResponseEntity<JsonNode> response = api.post("/api/payments", body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("errors").findValuesAsText("field")).containsExactlyInAnyOrder("amount", "receivedOn");
    }

    @Test
    void paymentsCanBeReadBack() {
        long customer = data.customer();
        data.invoice(customer, data.product("10.00", "ZERO"), 1);
        ResponseEntity<JsonNode> created = api.post("/api/payments", payment(customer, "10.00", null));

        JsonNode read = api.get(created.getHeaders().getLocation().getPath()).getBody();

        assertThat(read.get("amount").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(read.get("method").asText()).isEqualTo("BANK_TRANSFER");
        assertThat(read.get("reference").asText()).isEqualTo("BACS 12345");
    }

    private JsonNode invoice(JsonNode invoice) {
        return api.get("/api/invoices/" + invoice.get("id").asLong()).getBody();
    }

    private static Map<String, Object> payment(long customerId, String amount, List<Map<String, Object>> allocations) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customerId);
        body.put("amount", amount);
        body.put("receivedOn", LocalDate.now().toString());
        body.put("method", "BANK_TRANSFER");
        body.put("reference", "BACS 12345");
        if (allocations != null) {
            body.put("allocations", allocations);
        }
        return body;
    }
}
