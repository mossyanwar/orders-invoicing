package com.mossyanwar.ordersinvoicing.invoice;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long>, JpaSpecificationExecutor<Invoice> {

    boolean existsByOrderId(Long orderId);

    List<Invoice> findByCustomerIdAndStatusIn(Long customerId, Collection<InvoiceStatus> statuses);

    @Query("select i.id from Invoice i where i.status in :statuses and i.dueDate < :today order by i.id")
    List<Long> findIdsDueBefore(LocalDate today, Collection<InvoiceStatus> statuses);

    static Specification<Invoice> hasStatus(InvoiceStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Invoice> forCustomer(Long customerId) {
        return (root, query, cb) -> customerId == null ? null : cb.equal(root.get("customerId"), customerId);
    }
}
