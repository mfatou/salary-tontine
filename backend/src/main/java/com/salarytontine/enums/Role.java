package com.salarytontine.enums;

/**
 * Roles applicatifs. L'ordre de declaration reflete le niveau de privilège croissant.
 */
public enum Role {
    EMPLOYEE,
    ACCOUNTANT,
    ADMIN;

    /** Prefixe attendu par Spring Security pour les autorites de type role. */
    private static final String AUTHORITY_PREFIX = "ROLE_";

    public String asAuthority() {
        return AUTHORITY_PREFIX + name();
    }

    /**
     * Un administrateur gouverne l'application : comptes, rôles, audit. Il n'est
     * pas un salarié de l'entreprise, n'a donc pas de salaire de base et ne
     * participe pas aux tontines.
     *
     * <p>Le comptable, lui, est un employé comme les autres qui exerce en plus
     * une fonction : rien ne justifie de l'exclure des tontines.</p>
     */
    public boolean participatesInTontines() {
        return this != ADMIN;
    }
}
