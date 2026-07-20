package com.mossyanwar.ordersinvoicing.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issues invoices from many threads at once through the HTTP API and checks the numbers that come
 * out. The property gives this class its own Spring context and therefore its own empty database,
 * so it can assert the exact sequence 1..N.
 */
@IntegrationTest
@TestPropertySource(properties = "test.isolated-database=invoice-numbering")
class InvoiceNumberingConcurrencyIT {

    private static final int ORDERS = 40;
    private static final int THREADS = 8;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void parallelIssuingGivesUniqueNumbersWithNoGaps() throws Exception {
        Api api = new Api(rest);
        TestData data = new TestData(api);
        long customer = data.customer();
        long product = data.product("12.34", "STANDARD");
        List<Long> orderIds = new ArrayList<>();
        for (int i = 0; i < ORDERS; i++) {
            orderIds.add(data.fulfilledOrder(customer, product, 1 + i % 5));
        }
        // every order is requested twice at the same time: exactly one of each pair may win, and the
        // loser must not use up a number
        List<Long> requests = new ArrayList<>(orderIds);
        requests.addAll(orderIds);
        Collections.shuffle(requests);

        List<ResponseEntity<JsonNode>> responses = issueAllAtOnce(api, requests);

        List<ResponseEntity<JsonNode>> created = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CREATED).toList();
        List<ResponseEntity<JsonNode>> rejected = responses.stream()
                .filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).toList();
        assertThat(created).hasSize(ORDERS);
        assertThat(rejected).hasSize(ORDERS);

        List<String> numbers = created.stream().map(r -> r.getBody().get("invoiceNumber").asText()).toList();
        assertThat(numbers).doesNotHaveDuplicates();
        String year = numbers.getFirst().substring(4, 8);
        assertThat(numbers).allMatch(n -> n.startsWith("INV-" + year + "-"));
        assertThat(sequenceValues(numbers)).containsExactlyElementsOf(LongStream.rangeClosed(1, ORDERS).boxed().toList());

        List<String> stored = jdbc.queryForList("select invoice_number from invoice", String.class);
        assertThat(sequenceValues(stored)).containsExactlyElementsOf(LongStream.rangeClosed(1, ORDERS).boxed().toList());
        Long counter = jdbc.queryForObject(
                "select last_value from document_sequence where series = 'INVOICE' and doc_year = ?",
                Long.class, Integer.parseInt(year));
        assertThat(counter).isEqualTo(ORDERS);

        List<Long> invoicedOrders = created.stream().map(r -> r.getBody().get("orderId").asLong()).toList();
        assertThat(invoicedOrders).containsExactlyInAnyOrderElementsOf(orderIds);
    }

    private List<ResponseEntity<JsonNode>> issueAllAtOnce(Api api, List<Long> orderIds) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<JsonNode>>> futures = new ArrayList<>();
            for (long orderId : orderIds) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return api.post("/api/orders/" + orderId + "/invoice", null);
                }));
            }
            start.countDown();
            List<ResponseEntity<JsonNode>> responses = new ArrayList<>();
            for (Future<ResponseEntity<JsonNode>> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<Long> sequenceValues(List<String> invoiceNumbers) {
        return invoiceNumbers.stream()
                .map(n -> Long.parseLong(n.substring(n.lastIndexOf('-') + 1)))
                .sorted()
                .toList();
    }
}
