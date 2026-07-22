package com.mossyanwar.ordersinvoicing.invoice.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class InvoiceDocumentsIT {

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
    void downloadsTheInvoiceAsAPdf() throws Exception {
        JsonNode invoice = data.invoice(data.customer(), data.product("19.99", "STANDARD"), 7);
        String number = invoice.get("invoiceNumber").asText();

        ResponseEntity<byte[]> response = api.as("viewer").getRaw("/api/invoices/" + invoice.get("id").asLong() + "/pdf", byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains(number + ".pdf");
        try (PdfReader reader = new PdfReader(response.getBody())) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains(number, invoice.get("customerName").asText(), "£167.92");
        }
    }

    @Test
    void exportsInvoicesInADateRangeAsCsv() {
        long customer = api.post("/api/customers", Map.of(
                "name", "=HYPERLINK(\"http://evil.example\",\"click\")",
                "email", "csv-" + UUID.randomUUID() + "@example.test",
                "billingAddress", "1 Test Street",
                "paymentTermsDays", 30)).getBody().get("id").asLong();
        long product = data.product("10.00", "STANDARD");
        JsonNode first = data.invoice(customer, product, 1);
        JsonNode second = data.invoice(customer, product, 2);
        String today = first.get("issueDate").asText();

        ResponseEntity<String> response = api.as("viewer").getRaw("/api/invoices/export.csv?from=" + today + "&to=" + today, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().toString()).startsWith("text/csv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        List<String> lines = Arrays.asList(response.getBody().split("\r\n"));
        assertThat(lines.getFirst()).isEqualTo(String.join(",", InvoiceCsvExporter.HEADER));
        assertThat(lines).anyMatch(line -> line.startsWith(first.get("invoiceNumber").asText() + "," + today))
                .anyMatch(line -> line.startsWith(second.get("invoiceNumber").asText() + ","));
        String row = lines.stream().filter(line -> line.startsWith(second.get("invoiceNumber").asText())).findFirst().orElseThrow();
        assertThat(row).contains(",\"'=HYPERLINK(\"\"http://evil.example\"\",\"\"click\"\")\",")
                .endsWith(",ISSUED,20.00,4.00,24.00,0.00,0.00,24.00");
    }

    @Test
    void rejectsABackwardsRange() {
        LocalDate today = LocalDate.now();
        ResponseEntity<JsonNode> response = api.get("/api/invoices/export.csv?from=" + today + "&to=" + today.minusDays(1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("detail").asText()).contains("'to' must be on or after 'from'");
    }
}
