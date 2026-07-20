package com.mossyanwar.ordersinvoicing.invoice;

import com.fasterxml.jackson.databind.JsonNode;
import com.mossyanwar.ordersinvoicing.support.Api;
import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import com.mossyanwar.ordersinvoicing.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// own database, because marking overdue is a bulk operation over every invoice
@IntegrationTest
@TestPropertySource(properties = "test.isolated-database=overdue")
class OverdueInvoicesIT {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    OverdueInvoiceMarker marker;

    @Test
    void marksOnlyOpenInvoicesPastTheirDueDate() {
        Api api = new Api(rest);
        TestData data = new TestData(api);
        long priced = data.product("10.00", "STANDARD");
        long free = data.product("0.00", "ZERO");
        JsonNode tenDays = issue(api, data.fulfilledOrder(data.customer(10), priced, 1));
        JsonNode thirtyDays = issue(api, data.fulfilledOrder(data.customer(30), priced, 1));
        JsonNode alreadyPaid = issue(api, data.fulfilledOrder(data.customer(10), free, 1));
        LocalDate issued = LocalDate.parse(tenDays.get("issueDate").asText());

        assertThat(marker.markOverdue(issued.plusDays(10))).isZero();

        assertThat(marker.markOverdue(issued.plusDays(11))).isEqualTo(1);
        assertThat(status(api, tenDays)).isEqualTo("OVERDUE");
        assertThat(status(api, thirtyDays)).isEqualTo("ISSUED");
        assertThat(status(api, alreadyPaid)).isEqualTo("PAID");

        assertThat(marker.markOverdue(issued.plusDays(11))).as("running again changes nothing").isZero();

        assertThat(marker.markOverdue(issued.plusDays(31))).isEqualTo(1);
        assertThat(status(api, thirtyDays)).isEqualTo("OVERDUE");
    }

    private static JsonNode issue(Api api, long orderId) {
        return api.post("/api/orders/" + orderId + "/invoice", null).getBody();
    }

    private static String status(Api api, JsonNode invoice) {
        return api.get("/api/invoices/" + invoice.get("id").asLong()).getBody().get("status").asText();
    }
}
