package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.customer.Customer;
import com.mossyanwar.ordersinvoicing.customer.CustomerService;
import com.mossyanwar.ordersinvoicing.product.ProductService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.function.BiConsumer;

@Service
@Transactional
public class OrderService {

    private final OrderRepository orders;
    private final CustomerService customers;
    private final ProductService products;
    private final Clock clock;

    public OrderService(OrderRepository orders, CustomerService customers, ProductService products, Clock clock) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.clock = clock;
    }

    public OrderResponse create(OrderRequests.Create request) {
        Customer customer = customers.find(request.customerId());
        Order order = new Order(customer, clock.instant());
        for (OrderRequests.Line line : request.linesOrEmpty()) {
            order.addLine(products.find(line.productId()), line.quantity(), clock.instant());
        }
        return saveAndMap(order);
    }

    public OrderResponse addLine(long orderId, OrderRequests.Line request) {
        Order order = find(orderId);
        order.addLine(products.find(request.productId()), request.quantity(), clock.instant());
        return saveAndMap(order);
    }

    public OrderResponse removeLine(long orderId, long lineId) {
        Order order = find(orderId);
        order.removeLine(lineId, clock.instant());
        return saveAndMap(order);
    }

    public OrderResponse confirm(long orderId) {
        return transition(orderId, Order::confirm);
    }

    public OrderResponse fulfil(long orderId) {
        return transition(orderId, Order::fulfil);
    }

    public OrderResponse cancel(long orderId) {
        return transition(orderId, Order::cancel);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long orderId) {
        return OrderResponse.from(find(orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, Long customerId, Pageable pageable) {
        Specification<Order> filter = Specification.where(OrderRepository.hasStatus(status))
                .and(OrderRepository.forCustomer(customerId));
        return orders.findAll(filter, pageable).map(OrderResponse::from);
    }

    public Order find(long orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private OrderResponse transition(long orderId, BiConsumer<Order, Instant> action) {
        Order order = find(orderId);
        action.accept(order, clock.instant());
        return saveAndMap(order);
    }

    private OrderResponse saveAndMap(Order order) {
        Order saved = orders.saveAndFlush(order);
        return OrderResponse.from(saved);
    }
}
