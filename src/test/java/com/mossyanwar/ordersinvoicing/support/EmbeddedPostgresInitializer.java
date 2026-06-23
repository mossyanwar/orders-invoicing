package com.mossyanwar.ordersinvoicing.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates a fresh database for every Spring context the test framework builds and points the
 * datasource at it. Contexts are cached, so test classes with the same configuration share a
 * database; a test that needs a clean one can add a distinguishing property to get its own context.
 */
public class EmbeddedPostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final AtomicInteger DATABASE_COUNT = new AtomicInteger();

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        EmbeddedPostgres server = SharedPostgres.server();
        String database = "test_" + DATABASE_COUNT.incrementAndGet();
        try (Connection connection = server.getPostgresDatabase().getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create database " + database);
        } catch (SQLException e) {
            throw new IllegalStateException("could not create test database " + database, e);
        }
        TestPropertyValues.of(
                "spring.datasource.url=" + server.getJdbcUrl("postgres", database),
                "spring.datasource.username=postgres",
                "spring.datasource.password=postgres"
        ).applyTo(context);
    }
}
