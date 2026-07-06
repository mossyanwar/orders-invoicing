package com.mossyanwar.ordersinvoicing.customer;

import com.mossyanwar.ordersinvoicing.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customers;

    public CustomerController(CustomerService customers) {
        this.customers = customers;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequests.Create request) {
        CustomerResponse created = customers.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable long id) {
        return customers.get(id);
    }

    @GetMapping
    public PageResponse<CustomerResponse> list(@PageableDefault(sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return PageResponse.of(customers.list(pageable));
    }

    @PutMapping("/{id}")
    public CustomerResponse update(@PathVariable long id, @Valid @RequestBody CustomerRequests.Update request) {
        return customers.update(id, request);
    }
}
