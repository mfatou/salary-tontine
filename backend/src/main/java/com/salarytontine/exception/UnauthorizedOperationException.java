package com.salarytontine.exception;

/** Utilisateur authentifie mais non autorise sur la ressource : traduit en HTTP 403. */
public class UnauthorizedOperationException extends RuntimeException {

    public UnauthorizedOperationException(String message) {
        super(message);
    }
}
