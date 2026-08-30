package com.salarytontine.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Structure d'erreur unique exposee par l'API.
 * Aucune stack trace n'est jamais transmise au client.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse withValidation(int status,
                                               String error,
                                               String message,
                                               String path,
                                               Map<String, String> validationErrors) {
        return new ErrorResponse(Instant.now(), status, error, message, path, validationErrors);
    }
}
