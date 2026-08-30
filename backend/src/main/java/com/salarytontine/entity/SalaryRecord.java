package com.salarytontine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Salaire mensuel simule d'un participant.
 * Dans le MVP, un employé n'appartient qu'a une seule tontine ACTIVE,
 * ce qui garantit une seule ligne par utilisateur et par mois.
 */
@Entity
@Table(name = "salary_records", uniqueConstraints =
        @UniqueConstraint(name = "uk_salary_record_user_tontine_period",
                columnNames = {"user_id", "tontine_id", "period_index"}))
public class SalaryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tontine_id", nullable = false)
    private Tontine tontine;

    @Column(name = "salary_month", nullable = false, length = 7)
    private YearMonth salaryMonth;

    /** Rang du tour dans le cycle, à partir de 1. */
    @Column(name = "period_index", nullable = false)
    private Integer periodIndex;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "base_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "tontine_deduction", nullable = false, precision = 15, scale = 2)
    private BigDecimal tontineDeduction;

    @Column(name = "tontine_received", nullable = false, precision = 15, scale = 2)
    private BigDecimal tontineReceived;

    @Column(name = "final_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal finalSalary;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SalaryRecord() {
    }

    public SalaryRecord(User user,
                        Tontine tontine,
                        YearMonth salaryMonth,
                        Integer periodIndex,
                        LocalDate periodStart,
                        BigDecimal baseSalary,
                        BigDecimal tontineDeduction,
                        BigDecimal tontineReceived,
                        BigDecimal finalSalary) {
        this.user = user;
        this.tontine = tontine;
        this.salaryMonth = salaryMonth;
        this.periodIndex = periodIndex;
        this.periodStart = periodStart;
        this.baseSalary = baseSalary;
        this.tontineDeduction = tontineDeduction;
        this.tontineReceived = tontineReceived;
        this.finalSalary = finalSalary;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public boolean isBeneficiary() {
        return tontineReceived.signum() > 0;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Tontine getTontine() {
        return tontine;
    }

    public YearMonth getSalaryMonth() {
        return salaryMonth;
    }

    public Integer getPeriodIndex() {
        return periodIndex;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public BigDecimal getBaseSalary() {
        return baseSalary;
    }

    public BigDecimal getTontineDeduction() {
        return tontineDeduction;
    }

    public BigDecimal getTontineReceived() {
        return tontineReceived;
    }

    /**
     * Le salaire final porte le resultat du mois entier, toutes tontines
     * confondues. Il est donc reajuste des qu'une autre tontine généré ses
     * salaires pour le même mois.
     */
    public void setFinalSalary(BigDecimal finalSalary) {
        this.finalSalary = finalSalary;
    }

    public BigDecimal getFinalSalary() {
        return finalSalary;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SalaryRecord record) || id == null) {
            return false;
        }
        return Objects.equals(id, record.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
