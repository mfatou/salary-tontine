package com.salarytontine.enums;

/**
 * Etat d'une demande d'adhesion a une tontine.
 *
 * <p>Seul {@code ACCEPTED} donne lieu a une ligne dans {@code tontine_members} :
 * une demande en attente ne compte ni dans la cagnotte ni dans la durée du cycle.</p>
 */
public enum JoinRequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED
}
