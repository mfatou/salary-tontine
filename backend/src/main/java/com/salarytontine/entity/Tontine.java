package com.salarytontine.entity;

import com.salarytontine.enums.TontineFrequency;
import com.salarytontine.enums.TontineStatus;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "tontines")
public class Tontine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "monthly_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlyAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /**
     * Nombre de places declare a la création, facultatif.
     * Il plafonne les inscriptions et, surtout, fixe d'avance la fin du cycle :
     * une tontine dure un mois par participant.
     */
    @Column(name = "target_member_count")
    private Integer targetMemberCount;

    /** Longueur moyenne d'un mois, pour ramener une cadence en jours au mois. */
    private static final BigDecimal AVERAGE_MONTH_DAYS = new BigDecimal("30.4375");

    /** Cadence des tours. Le cycle dure toujours un tour par participant. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TontineFrequency frequency = TontineFrequency.MONTHLY;

    /**
     * Durée d'un tour en jours, renseignée uniquement pour une cadence libre.
     * Les cadences prédéfinies portent la leur : la dupliquer ici ferait
     * diverger deux sources de vérité pour la même valeur.
     */
    @Column(name = "period_days")
    private Integer periodDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TontineStatus status = TontineStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "tontine", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("turnOrder ASC")
    private List<TontineMember> members = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Tontine() {
    }

    public Tontine(String name, BigDecimal monthlyAmount, LocalDate startDate, User createdBy) {
        this(name, monthlyAmount, startDate, createdBy, null);
    }

    public Tontine(String name, BigDecimal monthlyAmount, LocalDate startDate, User createdBy,
                   Integer targetMemberCount) {
        this(name, monthlyAmount, startDate, createdBy, targetMemberCount, TontineFrequency.MONTHLY);
    }

    public Tontine(String name, BigDecimal monthlyAmount, LocalDate startDate, User createdBy,
                   Integer targetMemberCount, TontineFrequency frequency) {
        this(name, monthlyAmount, startDate, createdBy, targetMemberCount, frequency, null);
    }

    public Tontine(String name, BigDecimal monthlyAmount, LocalDate startDate, User createdBy,
                   Integer targetMemberCount, TontineFrequency frequency, Integer periodDays) {
        this.frequency = frequency == null ? TontineFrequency.MONTHLY : frequency;
        this.periodDays = this.frequency.requiresExplicitLength() ? periodDays : null;
        this.name = name;
        this.monthlyAmount = monthlyAmount;
        this.startDate = startDate;
        this.createdBy = createdBy;
        this.targetMemberCount = targetMemberCount;
        this.status = TontineStatus.DRAFT;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Premier mois du cycle, deduit de la date de début. */
    public YearMonth getStartMonth() {
        return YearMonth.from(startDate);
    }

    /**
     * Cagnotte distribuee chaque mois : cotisation mensuelle x nombre de participants.
     * Toujours calculee cote serveur, jamais fournie par le client.
     */
    public BigDecimal calculatePotAmount() {
        return monthlyAmount.multiply(BigDecimal.valueOf(members.size()));
    }

    /**
     * Duree du cycle, en mois. Tant que la tontine est ouverte, le nombre de
     * places declare fait foi : c'est ce qui permet d'annoncer la fin du cycle
     * avant que tous les participants soient inscrits. Une fois activée, la
     * composition est figée et seuls les participants réels comptent.
     */
    public int cycleLength() {
        if (isDraft() && targetMemberCount != null) {
            return targetMemberCount;
        }
        return members.size();
    }

    /**
     * Coût mensuel de la participation.
     *
     * <p>{@code monthlyAmount} est la cotisation d'un tour ; sur une cadence
     * infra-mensuelle, plusieurs tours tombent dans le même mois. C'est ce coût
     * ramené au mois, et non la cotisation brute, qui doit être comparé au
     * salaire.</p>
     */
    public BigDecimal monthlyCost() {
        return monthlyAmount.multiply(periodsPerMonth())
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Nombre moyen de tours dans un mois.
     *
     * <p>C'est une moyenne, pas un compte exact : un mois porte quatre ou cinq
     * tours hebdomadaires, jamais 4,35. Elle ne sert qu'à comparer des cadences
     * différentes sur une base commune.</p>
     */
    public BigDecimal periodsPerMonth() {
        Integer days = periodLengthInDays();
        if (days == null) {
            return BigDecimal.ONE;
        }
        return AVERAGE_MONTH_DAYS.divide(BigDecimal.valueOf(days), java.math.MathContext.DECIMAL64);
    }

    /**
     * Durée d'un tour en jours, ou {@code null} pour une cadence calendaire.
     * Une cadence libre lit sa durée sur la tontine, les autres sur l'enum.
     */
    public Integer periodLengthInDays() {
        return frequency.requiresExplicitLength() ? periodDays : frequency.getLengthInDays();
    }

    /** Premier jour du tour demandé, les tours étant numérotés à partir de 1. */
    public LocalDate periodStart(int periodIndex) {
        requirePositivePeriod(periodIndex);
        long elapsed = periodIndex - 1L;
        Integer days = periodLengthInDays();
        return days == null
                ? startDate.plusMonths(elapsed)
                : startDate.plusDays(elapsed * days);
    }

    /** Dernier jour du tour demandé, bornes incluses. */
    public LocalDate periodEnd(int periodIndex) {
        return periodStart(periodIndex + 1).minusDays(1);
    }

    /**
     * Rang du tour couvrant la date donnée, ou 0 lorsque la date précède le
     * début du cycle.
     */
    public int periodIndexOf(LocalDate date) {
        if (date.isBefore(startDate)) {
            return 0;
        }
        Integer days = periodLengthInDays();
        if (days == null) {
            return (int) java.time.temporal.ChronoUnit.MONTHS.between(startDate, date) + 1;
        }
        return (int) (java.time.temporal.ChronoUnit.DAYS.between(startDate, date) / days) + 1;
    }

    private void requirePositivePeriod(int periodIndex) {
        if (periodIndex < 1) {
            throw new IllegalArgumentException(
                    "Le rang d'un tour commence à 1 (reçu : %d).".formatted(periodIndex));
        }
    }

    /** Dernier jour du cycle, ou {@code null} tant qu'aucune durée n'est connue. */
    public LocalDate getProjectedEndDate() {
        int length = cycleLength();
        return length == 0 ? null : periodEnd(length);
    }

    /**
     * Mois du dernier tour. Conservé pour l'affichage : une tontine
     * infra-mensuelle se termine bien dans un mois donné.
     */
    public YearMonth getProjectedEndMonth() {
        LocalDate end = getProjectedEndDate();
        return end == null ? null : YearMonth.from(end);
    }

    /** Places encore disponibles, ou {@code null} si la tontine n'en declare pas. */
    public Integer getRemainingSeats() {
        if (targetMemberCount == null) {
            return null;
        }
        return Math.max(0, targetMemberCount - members.size());
    }

    public boolean isFull() {
        Integer remaining = getRemainingSeats();
        return remaining != null && remaining == 0;
    }

    public boolean isDraft() {
        return status == TontineStatus.DRAFT;
    }

    public boolean isActive() {
        return status == TontineStatus.ACTIVE;
    }

    public void addMember(TontineMember member) {
        members.add(member);
        member.setTontine(this);
    }

    public void removeMember(TontineMember member) {
        members.remove(member);
        member.setTontine(null);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getMonthlyAmount() {
        return monthlyAmount;
    }

    public void setMonthlyAmount(BigDecimal monthlyAmount) {
        this.monthlyAmount = monthlyAmount;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public TontineFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(TontineFrequency frequency) {
        this.frequency = frequency;
        if (!frequency.requiresExplicitLength()) {
            this.periodDays = null;
        }
    }

    public Integer getPeriodDays() {
        return periodDays;
    }

    public void setPeriodDays(Integer periodDays) {
        this.periodDays = periodDays;
    }

    public Integer getTargetMemberCount() {
        return targetMemberCount;
    }

    public void setTargetMemberCount(Integer targetMemberCount) {
        this.targetMemberCount = targetMemberCount;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public TontineStatus getStatus() {
        return status;
    }

    public void setStatus(TontineStatus status) {
        this.status = status;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public List<TontineMember> getMembers() {
        return members;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Tontine tontine) || id == null) {
            return false;
        }
        return Objects.equals(id, tontine.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
