package com.mossyanwar.ordersinvoicing.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * One embedded PostgreSQL server per test JVM, started lazily on first use.
 * Each Spring test context gets its own database on this server (see {@link EmbeddedPostgresInitializer}).
 */
final class SharedPostgres {

    private SharedPostgres() {
    }

    static EmbeddedPostgres server() {
        return Holder.SERVER;
    }

    private static final class Holder {

        private static final EmbeddedPostgres SERVER = start();

        private static EmbeddedPostgres start() {
            try {
                EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                        .setServerConfig("max_connections", "200")
                        .start();
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        postgres.close();
                    } catch (IOException ignored) {
                        // the JVM is exiting anyway
                    }
                }));
                return postgres;
            } catch (IOException e) {
                throw new UncheckedIOException("could not start embedded PostgreSQL", e);
            }
        }
    }
}
