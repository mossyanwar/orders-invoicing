package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.web.PageResponse;
import com.mossyanwar.ordersinvoicing.invoice.document.InvoiceCsvExporter;
import com.mossyanwar.ordersinvoicing.invoice.document.InvoicePdfRenderer;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api")
public class InvoiceController {

    private static final int MAX_EXPORT_DAYS = 366;

    private final InvoiceService invoices;
    private final InvoicePdfRenderer pdf;
    private final InvoiceCsvExporter csv;

    public InvoiceController(InvoiceService invoices, InvoicePdfRenderer pdf, InvoiceCsvExporter csv) {
        this.invoices = invoices;
        this.pdf = pdf;
        this.csv = csv;
    }

    @PostMapping("/orders/{orderId}/invoice")
    public ResponseEntity<InvoiceResponse> issue(@PathVariable long orderId) {
        InvoiceResponse invoice = invoices.issueForOrder(orderId);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/invoices/{id}").buildAndExpand(invoice.id()).toUri();
        return ResponseEntity.created(location).body(invoice);
    }

    @PostMapping("/invoices/{id:\\d+}/credit-notes")
    public ResponseEntity<CreditNoteResponse> credit(@PathVariable long id, @Valid @RequestBody CreditNoteRequest request) {
        CreditNoteResponse note = invoices.credit(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{noteId}").buildAndExpand(note.id()).toUri();
        return ResponseEntity.created(location).body(note);
    }

    @GetMapping("/invoices/{id:\\d+}/credit-notes/{noteId:\\d+}")
    public CreditNoteResponse creditNote(@PathVariable long id, @PathVariable long noteId) {
        return invoices.creditNote(id, noteId);
    }

    @GetMapping("/invoices/{id:\\d+}")
    public InvoiceResponse get(@PathVariable long id) {
        return invoices.get(id);
    }

    @GetMapping(value = "/invoices/{id:\\d+}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable long id) {
        InvoiceResponse invoice = invoices.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(invoice.invoiceNumber() + ".pdf").build().toString())
                .body(pdf.render(invoice));
    }

    /** Invoices issued between {@code from} and {@code to} inclusive, for at most a year at a time. */
    @GetMapping("/invoices/export.csv")
    public void exportCsv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          HttpServletResponse response) throws IOException {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_EXPORT_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "'to' must be on or after 'from' and the range at most " + MAX_EXPORT_DAYS + " days");
        }
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename("invoices-%s-to-%s.csv".formatted(from, to)).build().toString());
        csv.write(response.getWriter(), from, to);
    }

    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> list(@RequestParam(required = false) InvoiceStatus status,
                                              @RequestParam(required = false) Long customerId,
                                              @PageableDefault(sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(invoices.list(status, customerId, pageable));
    }
}
