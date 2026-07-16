package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.web.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceService invoices;

    public InvoiceController(InvoiceService invoices) {
        this.invoices = invoices;
    }

    @PostMapping("/orders/{orderId}/invoice")
    public ResponseEntity<InvoiceResponse> issue(@PathVariable long orderId) {
        InvoiceResponse invoice = invoices.issueForOrder(orderId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/invoices/{id}").buildAndExpand(invoice.id()).toUri();
        return ResponseEntity.created(location).body(invoice);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceResponse get(@PathVariable long id) {
        return invoices.get(id);
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> list(@RequestParam(required = false) InvoiceStatus status,
                                              @RequestParam(required = false) Long customerId,
                                              @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(invoices.list(status, customerId, pageable));
    }
}
