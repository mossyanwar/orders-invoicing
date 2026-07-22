package com.mossyanwar.ordersinvoicing.idempotency;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class IdempotencyIT {

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
    void aRetriedOrderIsCreatedOnceAndTheFirstResponseIsReplayed() {
        long customer = data.customer();
        Map<String, Object> order = orderFor(customer, data.product("3.00", "ZERO"));
        String key = UUID.randomUUID().toString();

        ResponseEntity<JsonNode> first = api.post("/api/orders", order, key);
        ResponseEntity<JsonNode> retry = api.post("/api/orders", order, key);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().containsKey(IdempotencyService.REPLAYED_HEADER)).isFalse();
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(retry.getHeaders().getFirst(IdempotencyService.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(retry.getHeaders().getLocation()).isEqualTo(first.getHeaders().getLocation());
        assertThat(retry.getBody()).isEqualTo(first.getBody());
        assertThat(ordersOf(customer)).isEqualTo(1);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRejected() {
        long customer = data.customer();
        long product = data.product("3.00", "ZERO");
        String key = UUID.randomUUID().toString();
        api.post("/api/orders", orderFor(customer, product), key);

        ResponseEntity<JsonNode> different = api.post("/api/orders", Map.of(
                "customerId", customer,
                "lines", List.of(Map.of("productId", product, "quantity", 99))), key);

        assertThat(different.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(different.getBody().get("type").asText()).endsWith("/idempotency-key");
        assertThat(ordersOf(customer)).isEqualTo(1);
    }

    @Test
    void keysBelongToTheUserWhoSentThem() {
        long customer = data.customer();
        long product = data.product("3.00", "ZERO");
        String key = "shared-key-" + UUID.randomUUID();

        ResponseEntity<JsonNode> clerks = api.as("clerk").post("/api/orders", orderFor(customer, product), key);
        ResponseEntity<JsonNode> admins = api.post("/api/orders", Map.of("customerId", customer), key);

        assertThat(clerks.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(admins.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(admins.getHeaders().containsKey(IdempotencyService.REPLAYED_HEADER)).isFalse();
        assertThat(ordersOf(customer)).isEqualTo(2);
    }

    @Test
    void theHeaderIsRequired() {
        ResponseEntity<JsonNode> response = api.post("/api/orders", orderFor(data.customer(), data.product("1.00", "ZERO")),
                new HttpHeaders());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).contains("Idempotency-Key");
    }

    @Test
    void malformedKeysAreRejected() {
        ResponseEntity<JsonNode> response = api.post("/api/orders", orderFor(data.customer(), data.product("1.00", "ZERO")),
                "has spaces in it");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Invalid Idempotency-Key");
    }

    @Test
    void aFailedRequestDoesNotUseUpItsKey() {
        long customer = data.customer();
        String key = UUID.randomUUID().toString();
        Map<String, Object> order = orderFor(customer, 987_654_321L);

        assertThat(api.post("/api/orders", order, key).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        long product = data.product("1.00", "ZERO");
        order = orderFor(customer, product);

        // the stored claim was released, so the same key now goes through with the corrected body
        assertThat(api.post("/api/orders", order, key).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void aRetriedPaymentIsTakenOnlyOnce() {
        long customer = data.customer();
        JsonNode invoice = data.invoice(customer, data.product("50.00", "ZERO"), 1);
        Map<String, Object> payment = Map.of(
                "customerId", customer,
                "amount", "20.00",
                "receivedOn", LocalDate.now().toString(),
                "method", "CARD");
        String key = UUID.randomUUID().toString();

        api.post("/api/payments", payment, key);
        ResponseEntity<JsonNode> retry = api.post("/api/payments", payment, key);

        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode after = api.get("/api/invoices/" + invoice.get("id").asLong()).getBody();
        assertThat(after.get("amountPaid").decimalValue()).isEqualByComparingTo("20.00");
    }

    @Test
    void simultaneousRequestsWithOneKeyCreateOneOrder() throws Exception {
        long customer = data.customer();
        Map<String, Object> order = orderFor(customer, data.product("2.00", "ZERO"));
        String key = UUID.randomUUID().toString();
        int attempts = 6;

        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return api.post("/api/orders", order, key);
            }));
        }
        start.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> future : futures) {
            statuses.add(HttpStatus.valueOf(future.get(30, TimeUnit.SECONDS).getStatusCode().value()));
        }
        pool.shutdown();

        // each request either created the order, replayed it, or was told the first is still running
        assertThat(statuses).containsAnyOf(HttpStatus.CREATED);
        assertThat(statuses).allMatch(s -> s == HttpStatus.CREATED || s == HttpStatus.CONFLICT);
        assertThat(ordersOf(customer)).isEqualTo(1);
    }

    private long ordersOf(long customer) {
        return api.get("/api/orders?customerId=" + customer).getBody().get("totalElements").asLong();
    }

    private static Map<String, Object> orderFor(long customer, long product) {
        return Map.of("customerId", customer, "lines", List.of(Map.of("productId", product, "quantity", 1)));
    }
}
