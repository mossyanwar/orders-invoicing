package com.mossyanwar.ordersinvoicing.payment;

import com.mossyanwar.ordersinvoicing.common.web.PageResponse;
import com.mossyanwar.ordersinvoicing.idempotency.IdempotencyService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;
    private final IdempotencyService idempotency;

    public PaymentController(PaymentService payments, IdempotencyService idempotency) {
        this.payments = payments;
        this.idempotency = idempotency;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> record(
            @Parameter(description = IdempotencyService.HEADER_DESCRIPTION)
            @RequestHeader(IdempotencyService.HEADER) String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return idempotency.execute("record-payment", idempotencyKey, request, PaymentResponse.class, () -> {
            PaymentResponse payment = payments.record(request);
            URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(payment.id()).toUri();
            return ResponseEntity.created(location).body(payment);
        });
    }

    @GetMapping("/{id}")
    public PaymentResponse get(@PathVariable long id) {
        return payments.get(id);
    }

    @GetMapping
    public PageResponse<PaymentResponse> list(@RequestParam(required = false) Long customerId,
                                              @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(payments.list(customerId, pageable));
    }
}
