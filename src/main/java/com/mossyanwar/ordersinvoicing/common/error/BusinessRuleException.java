package com.mossyanwar.ordersinvoicing.common.error;

/** The request was well formed but breaks a domain rule (for example paying more than is owed). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
