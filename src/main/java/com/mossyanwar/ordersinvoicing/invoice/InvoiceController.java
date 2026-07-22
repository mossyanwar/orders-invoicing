package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.web.PageResponse;
import com.mossyanwar.ordersinvoicing.invoice.document.InvoicePdfRenderer;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private final InvoiceService invoices;
    private final InvoicePdfRenderer pdf;

    public InvoiceController(InvoiceService invoices, InvoicePdfRenderer pdf) {
        this.invoices = invoices;
        this.pdf = pdf;
    }

    @PostMapping("/orders/{orderId}/invoice")
    public ResponseEntity<InvoiceResponse> issue(@PathVariable long orderId) {
        InvoiceResponse invoice = invoices.issueForOrder(orderId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/invoices/{id}").buildAndExpand(invoice.id()).toUri();
        return ResponseEntity.created(location).body(invoice);
    }

    @PostMapping("/invoices/{id}/credit-notes")
    public ResponseEntity<CreditNoteResponse> credit(@PathVariable long id, @Valid @RequestBody CreditNoteRequest request) {
        CreditNoteResponse note = invoices.credit(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{noteId}").buildAndExpand(note.id()).toUri();
        return ResponseEntity.created(location).body(note);
    }

    @GetMapping("/invoices/{id}/credit-notes/{noteId}")
    public CreditNoteResponse creditNote(@PathVariable long id, @PathVariable long noteId) {
        return invoices.creditNote(id, noteId);
    }

    @GetMapping("/invoices/{id}")
    public InvoiceResponse get(@PathVariable long id) {
        return invoices.get(id);
    }

    @GetMapping(value = "/invoices/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        InvoiceResponse invoice = invoices.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(invoice.invoiceNumber() + ".pdf").build().toString())
                .body(pdf.render(invoice));
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> list(@RequestParam(required = false) InvoiceStatus status,
                                              @RequestParam(required = false) Long customerId,
                                              @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(invoices.list(status, customerId, pageable));
    }
}
