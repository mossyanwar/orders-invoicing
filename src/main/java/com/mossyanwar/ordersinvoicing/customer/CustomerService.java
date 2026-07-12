package com.mossyanwar.ordersinvoicing.customer;

import com.mossyanwar.ordersinvoicing.common.error.ConcurrentUpdateException;
import com.mossyanwar.ordersinvoicing.common.error.ConflictException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customers;
    private final Clock clock;

    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    public CustomerResponse create(CustomerRequests.Create request) {
        if (customers.existsByEmail(Customer.normaliseEmail(request.email()))) {
            throw new ConflictException("A customer with email " + request.email() + " already exists");
        }
        Customer customer = new Customer(request.name(), request.email(), request.billingAddress(),
                request.paymentTermsOrDefault(), clock.instant());
        return CustomerResponse.from(customers.save(customer));
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(long id) {
        return CustomerResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(Pageable pageable) {
        return customers.findAll(pageable).map(CustomerResponse::from);
    }

    public CustomerResponse update(long id, CustomerRequests.Update request) {
        Customer customer = find(id);
        if (customer.getVersion() != request.version()) {
            throw new ConcurrentUpdateException("Customer", id, request.version(), customer.getVersion());
        }
        if (customers.existsByEmailAndIdNot(Customer.normaliseEmail(request.email()), id)) {
            throw new ConflictException("A customer with email " + request.email() + " already exists");
        }
        customer.update(request.name(), request.email(), request.billingAddress(), request.paymentTermsDays());
        customers.flush();
        return CustomerResponse.from(customer);
    }

    public Customer find(long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }
}
