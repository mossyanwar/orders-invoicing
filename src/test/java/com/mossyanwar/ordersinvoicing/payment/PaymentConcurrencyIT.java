package com.mossyanwar.ordersinvoicing.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class PaymentConcurrencyIT {

    private static final int RACERS = 4;

    @Autowired
    TestRestTemplate rest;

    @Test
    void racingPaymentsForTheWholeBalanceCannotBothSucceed() throws Exception {
        Api api = new Api(rest);
        TestData data = new TestData(api);
        long customer = data.customer();
        JsonNode invoice = data.invoice(customer, data.product("75.00", "STANDARD"), 1);   // 90.00 gross
        Map<String, Object> payment = Map.of(
                "customerId", customer,
                "amount", "90.00",
                "receivedOn", LocalDate.now().toString(),
                "method", "BANK_TRANSFER",
                "allocations", List.of(Map.of("invoiceId", invoice.get("id").asLong(), "amount", "90.00")));

        ExecutorService pool = Executors.newFixedThreadPool(RACERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
        for (int i = 0; i < RACERS; i++) {
            // different keys: these are genuinely different payments, not retries
            futures.add(pool.submit(() -> {
                start.await();
                return api.post("/api/payments", payment);
            }));
        }
        start.countDown();
        List<HttpStatus> statuses = new ArrayList<>();
        for (Future<ResponseEntity<JsonNode>> future : futures) {
            statuses.add(HttpStatus.valueOf(future.get(30, TimeUnit.SECONDS).getStatusCode().value()));
        }
        pool.shutdown();

        // the losers either hit the optimistic lock (409) or read the paid invoice afterwards (422)
        assertThat(statuses).filteredOn(s -> s == HttpStatus.CREATED).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != HttpStatus.CREATED)
                .allMatch(s -> s == HttpStatus.CONFLICT || s == HttpStatus.UNPROCESSABLE_ENTITY);

        JsonNode after = api.get("/api/invoices/" + invoice.get("id").asLong()).getBody();
        assertThat(after.get("amountPaid").decimalValue()).isEqualByComparingTo("90.00");
        assertThat(after.get("status").asText()).isEqualTo("PAID");
        assertThat(api.get("/api/payments?customerId=" + customer).getBody().get("totalElements").asLong()).isEqualTo(1);
    }
}
