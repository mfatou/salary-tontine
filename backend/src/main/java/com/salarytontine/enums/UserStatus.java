package com.salarytontine.enums;

public enum UserStatus {

    /** Inscription enregistrée, en attente de validation. */
    PENDING,

    /** Compte validé : la connexion est autorisée. */
    ACTIVE,

    /** Inscription refusée. Le compte est conservé pour la traçabilité. */
    REJECTED
}
