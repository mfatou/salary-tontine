package com.salarytontine.exception;

/** Ressource inexistante : traduit en HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceName, Object identifier) {
        return new ResourceNotFoundException("%s introuvable : %s".formatted(resourceName, identifier));
    }
}
