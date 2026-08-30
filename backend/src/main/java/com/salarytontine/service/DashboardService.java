package com.salarytontine.service;

import com.salarytontine.entity.SalaryRecord;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineMember;
import com.salarytontine.entity.User;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.repository.SalaryRecordRepository;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assemble les informations affichees sur le tableau de bord.
 * Toutes les données sont resolues a partir de l'utilisateur authentifie.
 */
@Service
public class DashboardService {

    private final TontineRepository tontineRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final TontineCycleService cycleService;
    private final CurrentUserProvider currentUserProvider;
    private final ContributionCapacityService capacityService;
    private final SalaryCalculator salaryCalculator;

    public DashboardService(TontineRepository tontineRepository,
                            SalaryRecordRepository salaryRecordRepository,
                            TontineCycleService cycleService,
                            CurrentUserProvider currentUserProvider,
                            ContributionCapacityService capacityService,
                            SalaryCalculator salaryCalculator) {
        this.tontineRepository = tontineRepository;
        this.salaryRecordRepository = salaryRecordRepository;
        this.cycleService = cycleService;
        this.currentUserProvider = currentUserProvider;
        this.capacityService = capacityService;
        this.salaryCalculator = salaryCalculator;
    }

    @Transactional(readOnly = true)
    public DashboardData load() {
        User user = currentUserProvider.requireUser();

        // Un employé peut cumuler les tontines : le tableau de bord met en avant
        // la plus ancienne et signale le nombre total.
        List<Long> activeTontineIds =
                tontineRepository.findIdsByMemberUserIdAndStatus(user.getId(), TontineStatus.ACTIVE);
        Optional<Tontine> activeTontine = activeTontineIds.stream()
                .findFirst()
                .flatMap(tontineRepository::findByIdWithMembers);

        Optional<TontineMember> membership = activeTontine.flatMap(tontine -> findMembership(tontine, user));
        Optional<SalaryRecord> latestRecord = salaryRecordRepository
                .findByUserIdWithDetails(user.getId()).stream().findFirst();

        return new DashboardData(
                user,
                activeTontine.orElse(null),
                membership.orElse(null),
                membership.map(member -> activeTontine.orElseThrow()
                        .periodStart(member.getTurnOrder())).orElse(null),
                activeTontine.flatMap(this::findNextSlot).orElse(null),
                latestRecord.orElse(null),
                activeTontineIds.size(),
                activeTontine.map(Tontine::calculatePotAmount).orElse(null),
                projectedTurnSalary(user, activeTontine.orElse(null)));
    }

    /**
     * Salaire estimé du mois où l'employé encaisse la cagnotte : son salaire de
     * base, diminué de l'ensemble de ses cotisations mensuelles, augmenté de la
     * cagnotte. C'est une projection, les montants définitifs étant calculés au
     * moment de la génération.
     */
    private BigDecimal projectedTurnSalary(User user, Tontine tontine) {
        if (tontine == null) {
            return null;
        }
        return salaryCalculator.calculate(
                user.getBaseSalary(),
                capacityService.engagedAmount(user.getId()),
                tontine.calculatePotAmount()).finalSalary();
    }

    private Optional<TontineMember> findMembership(Tontine tontine, User user) {
        return tontine.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(user.getId()))
                .findFirst();
    }

    /**
     * Prochain tour à venir, ou le tour en cours s'il n'est pas encore clos.
     * Retourne un résultat vide quand le cycle est terminé.
     */
    private Optional<TontineCycleService.CycleSlot> findNextSlot(Tontine tontine) {
        LocalDate today = LocalDate.now();
        return cycleService.buildSchedule(tontine).stream()
                .filter(slot -> !slot.end().isBefore(today))
                .findFirst();
    }

    /** Données brutes du tableau de bord, converties en DTO par le controleur. */
    public record DashboardData(
            User user,
            Tontine activeTontine,
            TontineMember membership,
            LocalDate myTurnDate,
            TontineCycleService.CycleSlot nextSlot,
            SalaryRecord latestSalaryRecord,
            int activeTontineCount,
            BigDecimal myTurnPotAmount,
            BigDecimal projectedTurnSalary) {
    }
}
