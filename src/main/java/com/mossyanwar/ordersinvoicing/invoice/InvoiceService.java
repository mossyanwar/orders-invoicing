package com.mossyanwar.ordersinvoicing.invoice;

import com.mossyanwar.ordersinvoicing.common.error.ConflictException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.invoice.numbering.DocumentNumberAllocator;
import com.mossyanwar.ordersinvoicing.invoice.numbering.DocumentSeries;
import com.mossyanwar.ordersinvoicing.order.Order;
import com.mossyanwar.ordersinvoicing.order.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
@Transactional
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final OrderService orders;
    private final DocumentNumberAllocator numbers;
    private final Clock clock;

    public InvoiceService(InvoiceRepository invoices, OrderService orders, DocumentNumberAllocator numbers, Clock clock) {
        this.invoices = invoices;
        this.orders = orders;
        this.numbers = numbers;
        this.clock = clock;
    }

    /**
     * Issues the invoice for a fulfilled order. Everything happens in one transaction: the order
     * moves to INVOICED, the number is allocated and the invoice is saved, or none of it happens.
     *
     * <p>The order row is locked first. Two invoices for one order would be a serious error, so a
     * second request for the same order waits here and then finds it already invoiced, instead of
     * racing ahead and taking a number it will have to give back. The unique constraint on
     * {@code invoice.order_id} is the backstop.
     */
    public InvoiceResponse issueForOrder(long orderId) {
        Order order = orders.findForUpdate(orderId);
        if (invoices.existsByOrderId(orderId)) {
            throw new ConflictException("Order " + orderId + " has already been invoiced");
        }
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock);

        order.markInvoiced(now);   // throws unless the order is FULFILLED

        // allocated last so the lock on the counter row is held for as short a time as possible
        String number = numbers.next(DocumentSeries.INVOICE, today.getYear());
        Invoice invoice = invoices.save(Invoice.issue(order, number, today, now));
        return InvoiceResponse.from(invoice);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(long invoiceId) {
        return InvoiceResponse.from(find(invoiceId));
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> list(InvoiceStatus status, Long customerId, Pageable pageable) {
        Specification<Invoice> filter = Specification.where(InvoiceRepository.hasStatus(status))
                .and(InvoiceRepository.forCustomer(customerId));
        return invoices.findAll(filter, pageable).map(InvoiceResponse::from);
    }

    public Invoice find(long invoiceId) {
        return invoices.findById(invoiceId).orElseThrow(() -> new NotFoundException("Invoice", invoiceId));
    }
}
