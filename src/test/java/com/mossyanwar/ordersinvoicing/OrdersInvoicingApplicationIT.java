package com.mossyanwar.ordersinvoicing;

import com.mossyanwar.ordersinvoicing.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class OrdersInvoicingApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void startsAgainstPostgres() {
        String version = jdbc.queryForObject("select version()", String.class);
        assertThat(version).startsWith("PostgreSQL 17");
    }
}
