package com.salarytontine.exception;

/** Regle metier violee : traduit en HTTP 400. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
