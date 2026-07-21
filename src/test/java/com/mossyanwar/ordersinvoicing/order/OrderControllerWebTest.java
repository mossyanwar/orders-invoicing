package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.common.error.InvalidStateTransitionException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.idempotency.IdempotencyService;
import com.mossyanwar.ordersinvoicing.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer only: routing, validation, security rules and error mapping, with the services mocked. */
@WebMvcTest(OrderController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class OrderControllerWebTest {

    private static final String VALID_ORDER = """
            {"customerId": 7, "lines": [{"productId": 3, "quantity": 2}]}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    OrderService orders;

    @MockitoBean
    IdempotencyService idempotency;

    @BeforeEach
    void idempotencyJustRunsTheAction() {
        when(idempotency.execute(anyString(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(4).get());
    }

    @Test
    void createsAnOrderAndReturnsItsLocation() throws Exception {
        when(orders.create(any())).thenReturn(order(42L, OrderStatus.DRAFT));

        mvc.perform(post("/api/orders").with(user("clerk").roles("CLERK"))
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/orders/42")))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(content().string(containsString("\"grossTotal\":24.00")));

        verify(orders).create(new OrderRequests.Create(7L, List.of(new OrderRequests.Line(3L, 2))));
        verify(idempotency).execute(eq("create-order"), eq("key-1"), any(), eq(OrderResponse.class), any());
    }

    @Test
    void invalidBodiesAreRejectedBeforeReachingTheService() throws Exception {
        mvc.perform(post("/api/orders").with(user("clerk").roles("CLERK"))
                        .header("Idempotency-Key", "key-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lines": [{"productId": 3, "quantity": 0}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value(endsWith("/validation")))
                .andExpect(jsonPath("$.errors[*].field").value(containsInAnyOrder("customerId", "lines[0].quantity")));

        verifyNoInteractions(orders);
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mvc.perform(post("/api/orders").with(user("clerk").roles("CLERK"))
                        .header("Idempotency-Key", "key-3")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"customerId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void theIdempotencyKeyHeaderIsRequired() throws Exception {
        mvc.perform(post("/api/orders").with(user("clerk").roles("CLERK"))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));

        verifyNoInteractions(orders);
    }

    @Test
    void anonymousRequestsGetA401Problem() throws Exception {
        mvc.perform(get("/api/orders/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Basic")))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void theConfiguredUsersCanLogIn() throws Exception {
        when(orders.get(1L)).thenReturn(order(1L, OrderStatus.DRAFT));

        mvc.perform(get("/api/orders/1").with(httpBasic("viewer", "viewer-test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void viewersCannotCreateOrders() throws Exception {
        mvc.perform(post("/api/orders").with(user("viewer").roles("VIEWER"))
                        .header("Idempotency-Key", "key-4")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_ORDER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        verifyNoInteractions(orders);
    }

    @Test
    void listsWithPaging() throws Exception {
        when(orders.list(eq(OrderStatus.DRAFT), isNull(), any()))
                .thenReturn(new PageImpl<>(List.of(order(1L, OrderStatus.DRAFT)), PageRequest.of(0, 1), 3));

        mvc.perform(get("/api/orders?status=DRAFT&size=1").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @Test
    void anIllegalTransitionIsAConflict() throws Exception {
        when(orders.fulfil(5L)).thenThrow(new InvalidStateTransitionException("Order", 5L, OrderStatus.DRAFT, OrderStatus.FULFILLED));

        mvc.perform(post("/api/orders/5/fulfil").with(user("clerk").roles("CLERK")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/invalid-state-transition")))
                .andExpect(jsonPath("$.currentStatus").value("DRAFT"))
                .andExpect(jsonPath("$.requestedStatus").value("FULFILLED"))
                .andExpect(jsonPath("$.instance").value("/api/orders/5/fulfil"));
    }

    @Test
    void anUnknownOrderIsNotFound() throws Exception {
        when(orders.get(99L)).thenThrow(new NotFoundException("Order", 99L));

        mvc.perform(get("/api/orders/99").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order 99 was not found"));
    }

    @Test
    void aNonNumericIdIsABadRequest() throws Exception {
        mvc.perform(get("/api/orders/abc").with(user("viewer").roles("VIEWER")))
                .andExpect(status().isBadRequest());
    }

    private static OrderResponse order(long id, OrderStatus status) {
        Money unit = Money.of("10.00");
        OrderResponse.Line line = new OrderResponse.Line(1L, 3L, "W-1", "Widget", 2, unit, VatRate.STANDARD,
                Money.of("20.00"), Money.of("4.00"), Money.of("24.00"));
        return new OrderResponse(id, 7L, status, List.of(line), Money.of("20.00"), Money.of("4.00"), Money.of("24.00"),
                Instant.parse("2026-03-02T09:00:00Z"), Instant.parse("2026-03-02T09:00:00Z"), 0);
    }
}
