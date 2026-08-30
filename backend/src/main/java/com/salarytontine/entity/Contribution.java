package com.salarytontine.entity;

import com.salarytontine.enums.ContributionStatus;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

@Entity
@Table(name = "contributions", uniqueConstraints =
        @UniqueConstraint(name = "uk_contribution_tontine_user_period",
                columnNames = {"tontine_id", "user_id", "period_index"}))
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tontine_id", nullable = false)
    private Tontine tontine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Rang du tour dans le cycle, à partir de 1. */
    @Column(name = "period_index", nullable = false)
    private Integer periodIndex;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ContributionStatus status = ContributionStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Contribution() {
    }

    public Contribution(Tontine tontine, User user, BigDecimal amount,
                        Integer periodIndex, LocalDate periodStart) {
        this.tontine = tontine;
        this.user = user;
        this.amount = amount;
        this.periodIndex = periodIndex;
        this.periodStart = periodStart;
        this.status = ContributionStatus.PENDING;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void markDeducted() {
        this.status = ContributionStatus.DEDUCTED;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public Integer getPeriodIndex() {
        return periodIndex;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    /** Mois de rattachement, utilisé par le bulletin de salaire. */
    public YearMonth getContributionMonth() {
        return YearMonth.from(periodStart);
    }

    public ContributionStatus getStatus() {
        return status;
    }

    public void setStatus(ContributionStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Contribution contribution) || id == null) {
            return false;
        }
        return Objects.equals(id, contribution.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
