package com.mossyanwar.ordersinvoicing.product;

import com.mossyanwar.ordersinvoicing.common.error.ConcurrentUpdateException;
import com.mossyanwar.ordersinvoicing.common.money.Money;
import com.mossyanwar.ordersinvoicing.common.money.VatRate;
import com.mossyanwar.ordersinvoicing.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ProductControllerWebTest {

    private static final String PRODUCT = """
            {"sku": "W-1", "name": "Widget", "unitPrice": "10.00", "vatRate": "STANDARD"}
            """;

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductService products;

    @Test
    void onlyAdminsCanAddProducts() throws Exception {
        mvc.perform(post("/api/products").with(user("clerk").roles("CLERK"))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isForbidden());

        verifyNoInteractions(products);
    }

    @Test
    void adminsCanAddProducts() throws Exception {
        when(products.create(any())).thenReturn(new ProductResponse(1L, "W-1", "Widget", Money.of("10.00"), VatRate.STANDARD, true, 0));

        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(PRODUCT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("W-1"));
    }

    @Test
    void pricesWithFractionsOfAPennyAreRejected() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "W-1", "name": "Widget", "unitPrice": "10.001", "vatRate": "STANDARD"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("unitPrice"));

        verifyNoInteractions(products);
    }

    @Test
    void unknownVatRatesAreRejected() throws Exception {
        mvc.perform(post("/api/products").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku": "W-1", "name": "Widget", "unitPrice": "10.00", "vatRate": "LUXURY"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aStaleVersionIsAConflict() throws Exception {
        when(products.update(eq(1L), any())).thenThrow(new ConcurrentUpdateException("Product", 1L, 0, 1));

        mvc.perform(put("/api/products/1").with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Widget", "unitPrice": "11.00", "vatRate": "STANDARD", "active": true, "version": 0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(endsWith("/concurrent-update")))
                .andExpect(jsonPath("$.detail").value("Product 1 is at version 1 but the request was based on version 0"));
    }
}
