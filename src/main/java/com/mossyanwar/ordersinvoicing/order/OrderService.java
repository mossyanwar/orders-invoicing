package com.mossyanwar.ordersinvoicing.order;

import com.mossyanwar.ordersinvoicing.audit.AuditLog;
import com.mossyanwar.ordersinvoicing.audit.AuditedEntity;
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
    private final AuditLog audit;
    private final Clock clock;

    public OrderService(OrderRepository orders, CustomerService customers, ProductService products, AuditLog audit,
                        Clock clock) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.audit = audit;
        this.clock = clock;
    }

    public OrderResponse create(OrderRequests.Create request) {
        Customer customer = customers.find(request.customerId());
        Order order = new Order(customer, clock.instant());
        for (OrderRequests.Line line : request.linesOrEmpty()) {
            order.addLine(products.find(line.productId()), line.quantity(), clock.instant());
        }
        orders.save(order);
        audit.record(AuditedEntity.ORDER, order.getId(), "CREATED", null, order.getStatus(),
                order.getLines().size() + " line(s) for customer " + customer.getId());
        return flushAndMap(order);
    }

    public OrderResponse addLine(long orderId, OrderRequests.Line request) {
        Order order = find(orderId);
        OrderLine line = order.addLine(products.find(request.productId()), request.quantity(), clock.instant());
        audit.record(AuditedEntity.ORDER, orderId, "LINE_ADDED", line.getSku() + " x " + line.getQuantity());
        return flushAndMap(order);
    }

    public OrderResponse removeLine(long orderId, long lineId) {
        Order order = find(orderId);
        order.removeLine(lineId, clock.instant());
        audit.record(AuditedEntity.ORDER, orderId, "LINE_REMOVED", "line " + lineId);
        return flushAndMap(order);
    }

    public OrderResponse confirm(long orderId) {
        return transition(orderId, Order::confirm, "CONFIRMED");
    }

    public OrderResponse fulfil(long orderId) {
        return transition(orderId, Order::fulfil, "FULFILLED");
    }

    public OrderResponse cancel(long orderId) {
        return transition(orderId, Order::cancel, "CANCELLED");
    }

    @Transactional(readOnly = true)
    public OrderResponse get(long orderId) {
        return OrderResponse.from(find(orderId));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> list(OrderStatus status, Long customerId, Pageable pageable) {
        Specification<Order> filter = Specification.allOf(OrderRepository.hasStatus(status),
                OrderRepository.forCustomer(customerId));
        return orders.findAll(filter, pageable).map(OrderResponse::from);
    }

    public Order find(long orderId) {
        return orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    /** Loads the order with a row lock held until the caller's transaction ends. */
    public Order findForUpdate(long orderId) {
        return orders.findByIdForUpdate(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
    }

    private OrderResponse transition(long orderId, BiConsumer<Order, Instant> action, String auditAction) {
        Order order = find(orderId);
        OrderStatus from = order.getStatus();
        action.accept(order, clock.instant());
        audit.record(AuditedEntity.ORDER, orderId, auditAction, from, order.getStatus(), null);
        return flushAndMap(order);
    }

    /**
     * The order is already managed, so changes are written on flush; calling {@code save} again
     * would merge it and copy any new lines instead of persisting the instances we hold. Flushing
     * here means the response carries the new version and line ids.
     */
    private OrderResponse flushAndMap(Order order) {
        orders.flush();
        return OrderResponse.from(order);
    }
}
