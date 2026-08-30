package com.salarytontine.exception;

/** Ressource déjà existante ou opération déjà effectuee : traduit en HTTP 409. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
