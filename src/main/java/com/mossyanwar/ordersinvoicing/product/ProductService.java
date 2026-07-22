package com.mossyanwar.ordersinvoicing.product;

import com.mossyanwar.ordersinvoicing.audit.AuditLog;
import com.mossyanwar.ordersinvoicing.audit.AuditedEntity;
import com.mossyanwar.ordersinvoicing.common.error.ConcurrentUpdateException;
import com.mossyanwar.ordersinvoicing.common.error.ConflictException;
import com.mossyanwar.ordersinvoicing.common.error.NotFoundException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@Transactional
public class ProductService {

    private final ProductRepository products;
    private final AuditLog audit;

    public ProductService(ProductRepository products, AuditLog audit) {
        this.products = products;
        this.audit = audit;
    }

    public ProductResponse create(ProductRequests.Create request) {
        if (products.existsBySku(request.sku().strip().toUpperCase(Locale.ROOT))) {
            throw new ConflictException("A product with SKU " + request.sku() + " already exists");
        }
        Product product = products.save(new Product(request.sku(), request.name(), Money.of(request.unitPrice()), request.vatRate()));
        audit.record(AuditedEntity.PRODUCT, product.getId(), "CREATED",
                "%s at %s, VAT %s".formatted(product.getSku(), product.getUnitPrice(), product.getVatRate()));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(long id) {
        return ProductResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(boolean includeInactive, Pageable pageable) {
        Page<Product> page = includeInactive ? products.findAll(pageable) : products.findByActiveTrue(pageable);
        return page.map(ProductResponse::from);
    }

    public ProductResponse update(long id, ProductRequests.Update request) {
        Product product = find(id);
        if (product.getVersion() != request.version()) {
            throw new ConcurrentUpdateException("Product", id, request.version(), product.getVersion());
        }
        String before = "%s, VAT %s, active %s".formatted(product.getUnitPrice(), product.getVatRate(), product.isActive());
        product.update(request.name(), Money.of(request.unitPrice()), request.vatRate(), request.active());
        products.flush();
        String after = "%s, VAT %s, active %s".formatted(product.getUnitPrice(), product.getVatRate(), product.isActive());
        audit.record(AuditedEntity.PRODUCT, id, "UPDATED", before.equals(after) ? after : before + " -> " + after);
        return ProductResponse.from(product);
    }

    public Product find(long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }
}
