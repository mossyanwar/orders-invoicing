package com.mossyanwar.ordersinvoicing.common.error;

import java.net.URI;

/** {@code type} URIs used in RFC 7807 problem responses. */
public final class ProblemTypes {

    private static final String BASE = "https://orders-invoicing.example/problems/";

    public static final URI NOT_FOUND = URI.create(BASE + "not-found");
    public static final URI VALIDATION = URI.create(BASE + "validation");
    public static final URI BUSINESS_RULE = URI.create(BASE + "business-rule");
    public static final URI CONFLICT = URI.create(BASE + "conflict");
    public static final URI INVALID_STATE_TRANSITION = URI.create(BASE + "invalid-state-transition");
    public static final URI CONCURRENT_UPDATE = URI.create(BASE + "concurrent-update");
    public static final URI INTERNAL = URI.create(BASE + "internal");

    private ProblemTypes() {
    }
}
