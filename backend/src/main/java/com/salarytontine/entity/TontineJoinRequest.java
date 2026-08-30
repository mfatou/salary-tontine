package com.salarytontine.entity;

import com.salarytontine.enums.JoinRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

/**
 * Demande d'un employé pour rejoindre une tontine ouverte aux inscriptions.
 *
 * <p>La demande est volontairement distincte de {@link TontineMember} : tant
 * qu'elle n'est pas acceptee, le demandeur n'est pas participant et ne doit
 * influer ni sur la cagnotte, ni sur la durée du cycle, ni sur les salaires.</p>
 */
@Entity
@Table(name = "tontine_join_requests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_join_request_tontine_user", columnNames = {"tontine_id", "user_id"})
})
public class TontineJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tontine_id", nullable = false)
    private Tontine tontine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JoinRequestStatus status = JoinRequestStatus.PENDING;

    @Column(length = 300)
    private String motivation;

    @Column(name = "decision_note", length = 300)
    private String decisionNote;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    protected TontineJoinRequest() {
    }

    public TontineJoinRequest(Tontine tontine, User user, String motivation) {
        this.tontine = tontine;
        this.user = user;
        this.motivation = motivation;
        this.status = JoinRequestStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.requestedAt = Instant.now();
    }

    public boolean isPending() {
        return status == JoinRequestStatus.PENDING;
    }

    /** Remet une demande refusée a l'etat initial : l'employé retente sa chance. */
    public void reopen(String newMotivation) {
        this.status = JoinRequestStatus.PENDING;
        this.motivation = newMotivation;
        this.decisionNote = null;
        this.decidedAt = null;
        this.decidedBy = null;
        this.requestedAt = Instant.now();
    }

    public void accept(User decidedByUser) {
        applyDecision(JoinRequestStatus.ACCEPTED, decidedByUser, null);
    }

    public void reject(User decidedByUser, String reason) {
        applyDecision(JoinRequestStatus.REJECTED, decidedByUser, reason);
    }

    private void applyDecision(JoinRequestStatus decision, User decidedByUser, String note) {
        this.status = decision;
        this.decidedBy = decidedByUser;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Tontine getTontine() {
        return tontine;
    }

    public User getUser() {
        return user;
    }

    public JoinRequestStatus getStatus() {
        return status;
    }

    public String getMotivation() {
        return motivation;
    }

    public String getDecisionNote() {
        return decisionNote;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Instant getDecidedAt() {
        return decidedAt;
    }

    public User getDecidedBy() {
        return decidedBy;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TontineJoinRequest request) || id == null) {
            return false;
        }
        return Objects.equals(id, request.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
