package com.mossyanwar.ordersinvoicing.product;

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

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public ProductResponse create(ProductRequests.Create request) {
        if (products.existsBySku(request.sku().strip().toUpperCase(Locale.ROOT))) {
            throw new ConflictException("A product with SKU " + request.sku() + " already exists");
        }
        Product product = new Product(request.sku(), request.name(), Money.of(request.unitPrice()), request.vatRate());
        return ProductResponse.from(products.save(product));
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
        product.update(request.name(), Money.of(request.unitPrice()), request.vatRate(), request.active());
        products.flush();
        return ProductResponse.from(product);
    }

    public Product find(long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }
}
