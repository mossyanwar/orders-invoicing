package com.mossyanwar.ordersinvoicing.payment;

import com.mossyanwar.ordersinvoicing.audit.AuditLog;
import com.mossyanwar.ordersinvoicing.audit.AuditedEntity;
import com.mossyanwar.ordersinvoicing.common.error.BusinessRuleException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.customer.Customer;
import com.mossyanwar.ordersinvoicing.customer.CustomerService;
import com.mossyanwar.ordersinvoicing.invoice.Invoice;
import com.mossyanwar.ordersinvoicing.invoice.InvoiceService;
import com.mossyanwar.ordersinvoicing.invoice.InvoiceStatus;
import com.mossyanwar.ordersinvoicing.payment.PaymentAllocator.Allocation;
import com.mossyanwar.ordersinvoicing.payment.PaymentAllocator.OpenInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class PaymentService {

    private final PaymentRepository payments;
    private final CustomerService customers;
    private final InvoiceService invoices;
    private final AuditLog audit;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, CustomerService customers, InvoiceService invoices,
                          AuditLog audit, Clock clock) {
        this.payments = payments;
        this.customers = customers;
        this.invoices = invoices;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * Records a payment and applies it to invoices in one transaction. The invoices are protected
     * by their {@code @Version}: if another payment or credit note changes one of them first, the
     * flush fails with an optimistic locking error, nothing is saved, and the client gets a 409 and
     * can retry with fresh balances.
     */
    public PaymentResponse record(PaymentRequest request) {
        Customer customer = customers.find(request.customerId());
        Money amount = Money.of(request.amount());
        Instant now = clock.instant();

        List<Allocation> plan = request.hasExplicitAllocations()
                ? PaymentAllocator.explicit(amount, request.allocations().stream()
                        .map(a -> new Allocation(a.invoiceId(), Money.of(a.amount())))
                        .toList())
                : PaymentAllocator.oldestFirst(amount, invoices.openInvoicesOf(customer.getId()).stream()
                        .map(i -> new OpenInvoice(i.getId(), i.getDueDate(), i.outstanding()))
                        .toList());

        Payment payment = new Payment(customer.getId(), amount, request.receivedOn(), request.method(),
                request.reference(), now);
        for (Allocation allocation : plan) {
            Invoice invoice = invoices.find(allocation.invoiceId());
            if (!Objects.equals(invoice.getCustomerId(), customer.getId())) {
                throw new BusinessRuleException("Invoice %s belongs to a different customer".formatted(invoice.getInvoiceNumber()));
            }
            InvoiceStatus was = invoice.getStatus();
            payment.allocateTo(invoice, allocation.amount(), now);
            audit.record(AuditedEntity.INVOICE, invoice.getId(), "PAYMENT_APPLIED", was, invoice.getStatus(),
                    allocation.amount() + " applied, " + invoice.outstanding() + " outstanding");
        }
        payments.save(payment);
        payments.flush();   // surfaces a version clash here, inside the service, rather than at commit
        audit.record(AuditedEntity.PAYMENT, payment.getId(), "RECORDED",
                "%s by %s from customer %d".formatted(amount, request.method(), customer.getId()));
        return PaymentResponse.from(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse get(long paymentId) {
        return payments.findById(paymentId).map(PaymentResponse::from)
                .orElseThrow(() -> new NotFoundException("Payment", paymentId));
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(Long customerId, Pageable pageable) {
        Page<Payment> page = customerId == null ? payments.findAll(pageable) : payments.findByCustomerId(customerId, pageable);
        return page.map(PaymentResponse::from);
    }
}
