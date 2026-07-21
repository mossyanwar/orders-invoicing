package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.common.web.PageResponse;
import com.mossyanwar.ordersinvoicing.idempotency.IdempotencyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orders;
    private final IdempotencyService idempotency;

    public OrderController(OrderService orders, IdempotencyService idempotency) {
        this.orders = orders;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
                                                @Valid @RequestBody OrderRequests.Create request) {
        return idempotency.execute("create-order", idempotencyKey, request, OrderResponse.class, () -> {
            OrderResponse created = orders.create(request);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
            return ResponseEntity.created(location).body(created);
        });
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable long id) {
        return orders.get(id);
    }

    @GetMapping
    public PageResponse<OrderResponse> list(@RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) Long customerId,
                                            @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(orders.list(status, customerId, pageable));
    }

    @PostMapping("/{id}/lines")
    public OrderResponse addLine(@PathVariable long id, @Valid @RequestBody OrderRequests.Line request) {
        return orders.addLine(id, request);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    public OrderResponse removeLine(@PathVariable long id, @PathVariable long lineId) {
        return orders.removeLine(id, lineId);
    }

    @PostMapping("/{id}/confirm")
    public OrderResponse confirm(@PathVariable long id) {
        return orders.confirm(id);
    }

    @PostMapping("/{id}/fulfil")
    public OrderResponse fulfil(@PathVariable long id) {
        return orders.fulfil(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable long id) {
        return orders.cancel(id);
    }
}
