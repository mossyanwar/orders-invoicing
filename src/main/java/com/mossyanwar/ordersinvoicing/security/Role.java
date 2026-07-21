package com.mossyanwar.ordersinvoicing.security;

/**
 * ADMIN implies CLERK, which implies VIEWER (see {@link SecurityConfig#roleHierarchy()}).
 * <ul>
 *   <li>VIEWER: read anything except the audit log</li>
 *   <li>CLERK: also create customers, orders, invoices and payments</li>
 *   <li>ADMIN: also manage the product catalogue, issue credit notes, run jobs and read the audit log</li>
 * </ul>
 */
public enum Role {
    ADMIN,
    CLERK,
    VIEWER
}
