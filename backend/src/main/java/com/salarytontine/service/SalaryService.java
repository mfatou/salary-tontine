package com.salarytontine.service;

import com.salarytontine.entity.Contribution;
import com.salarytontine.entity.SalaryRecord;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineMember;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.exception.ResourceNotFoundException;
import com.salarytontine.repository.ContributionRepository;
import com.salarytontine.repository.SalaryRecordRepository;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moteur de simulation salariale.
 *
 * <p>Pour un mois donne, chaque participant voit sa cotisation déduite de son
 * salaire de base ; le bénéficiaire du tour percoit en plus la cagnotte.
 * Toutes les valeurs sont calculees ici, cote serveur : le client ne peut
 * fournir ni cagnotte, ni déduction, ni salaire final.</p>
 */
@Service
public class SalaryService {

    private static final String AUDIT_ENTITY = "Tontine";

    private final SalaryRecordRepository salaryRecordRepository;
    private final ContributionRepository contributionRepository;
    private final TontineRepository tontineRepository;
    private final TontineService tontineService;
    private final TontineCycleService cycleService;
    private final SalaryCalculator salaryCalculator;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public SalaryService(SalaryRecordRepository salaryRecordRepository,
                         ContributionRepository contributionRepository,
                         TontineRepository tontineRepository,
                         TontineService tontineService,
                         TontineCycleService cycleService,
                         SalaryCalculator salaryCalculator,
                         CurrentUserProvider currentUserProvider,
                         AuditService auditService) {
        this.salaryRecordRepository = salaryRecordRepository;
        this.contributionRepository = contributionRepository;
        this.tontineRepository = tontineRepository;
        this.tontineService = tontineService;
        this.cycleService = cycleService;
        this.salaryCalculator = salaryCalculator;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    /**
     * Calcule et enregistre les salaires simules du mois, puis marque les
     * cotisations correspondantes comme deduites. L'opération est atomique.
     */
    @Transactional
    public List<SalaryRecord> generateForPeriod(Long tontineId, int periodIndex) {
        Tontine tontine = tontineService.findByIdWithMembers(tontineId);
        requireActive(tontine);
        cycleService.requireWithinCycle(tontine, periodIndex);

        if (salaryRecordRepository.existsByTontineIdAndPeriodIndex(tontine.getId(), periodIndex)) {
            throw new DuplicateResourceException(
                    "Les salaires du tour %d ont déjà été générés pour cette tontine."
                            .formatted(periodIndex));
        }

        List<Contribution> contributions =
                contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(tontine.getId(), periodIndex);
        requireContributionsForAllMembers(tontine, periodIndex, contributions);

        LocalDate periodStart = tontine.periodStart(periodIndex);
        // Le bulletin de paie reste mensuel : une tontine infra-mensuelle
        // rattache plusieurs tours au même mois.
        YearMonth month = YearMonth.from(periodStart);

        TontineMember beneficiary = cycleService.resolveBeneficiary(tontine, periodIndex);
        BigDecimal potAmount = salaryCalculator.calculatePot(
                tontine.getMonthlyAmount(), tontine.getMembers().size());

        Map<Long, Contribution> contributionsByUserId = contributions.stream()
                .collect(Collectors.toMap(contribution -> contribution.getUser().getId(),
                        Function.identity()));

        List<SalaryRecord> records = tontine.getMembers().stream()
                .map(member -> buildRecord(tontine, member, month, periodIndex, periodStart,
                        beneficiary, potAmount,
                        contributionsByUserId.get(member.getUser().getId())))
                .toList();

        List<SalaryRecord> saved = salaryRecordRepository.saveAll(records);
        salaryRecordRepository.flush();
        recomputeMonthlyTotals(saved, month);

        contributions.forEach(Contribution::markDeducted);
        contributionRepository.saveAll(contributions);

        completeCycleIfLastPeriod(tontine, periodIndex);

        auditService.record(currentUserProvider.findAuditAuthor(), AuditAction.SALARIES_GENERATED,
                AUDIT_ENTITY, tontine.getId(),
                "Génération de %d salaires pour le tour %d (%s), cagnotte de %s versée à %s"
                        .formatted(saved.size(), periodIndex, periodStart, potAmount,
                                beneficiary.getUser().getName()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SalaryRecord> findMySalaryRecords() {
        return salaryRecordRepository.findByUserIdWithDetails(currentUserProvider.requireUserId());
    }

    /**
     * Bulletin consolide du mois pour l'utilisateur authentifie.
     *
     * <p>Un employé pouvant cotiser a plusieurs tontines, le mois porte une
     * ligne par tontine et un seul resultat.</p>
     */
    @Transactional(readOnly = true)
    public MonthlyStatement findMyMonthlyStatement(YearMonth month) {
        Long userId = currentUserProvider.requireUserId();
        List<SalaryRecord> lines = salaryRecordRepository.findByUserIdAndSalaryMonth(userId, month);

        if (lines.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Aucun salaire simule enregistre pour %s.".formatted(month));
        }
        return toStatement(month, lines);
    }

    private MonthlyStatement toStatement(YearMonth month, List<SalaryRecord> lines) {
        BigDecimal baseSalary = lines.getFirst().getBaseSalary();
        BigDecimal totalDeduction = sum(lines, SalaryRecord::getTontineDeduction);
        BigDecimal totalReceived = sum(lines, SalaryRecord::getTontineReceived);

        SalaryCalculator.SimulatedSalary consolidated =
                salaryCalculator.calculate(baseSalary, totalDeduction, totalReceived);

        return new MonthlyStatement(month, baseSalary, totalDeduction, totalReceived,
                consolidated.finalSalary(), lines);
    }

    /**
     * Aligne le salaire final de toutes les lignes du mois sur le resultat
     * consolide. Sans cela, un employé cotisant a deux tontines lirait sur
     * chaque ligne un salaire qui ignore l'autre prélèvement.
     */
    private void recomputeMonthlyTotals(List<SalaryRecord> saved, YearMonth month) {
        List<Long> affectedUserIds = saved.stream()
                .map(record -> record.getUser().getId())
                .distinct()
                .toList();

        for (Long userId : affectedUserIds) {
            List<SalaryRecord> monthly =
                    salaryRecordRepository.findByUserIdAndSalaryMonthOrderByIdAsc(userId, month);
            if (monthly.isEmpty()) {
                continue;
            }
            BigDecimal consolidated = salaryCalculator.calculate(
                    monthly.getFirst().getBaseSalary(),
                    sum(monthly, SalaryRecord::getTontineDeduction),
                    sum(monthly, SalaryRecord::getTontineReceived)).finalSalary();

            monthly.forEach(record -> record.setFinalSalary(consolidated));
            salaryRecordRepository.saveAll(monthly);
        }
    }

    private BigDecimal sum(List<SalaryRecord> records,
                           java.util.function.Function<SalaryRecord, BigDecimal> field) {
        return records.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Resultat consolide d'un mois et les lignes qui le composent. */
    public record MonthlyStatement(
            YearMonth month,
            BigDecimal baseSalary,
            BigDecimal totalDeduction,
            BigDecimal totalReceived,
            BigDecimal finalSalary,
            List<SalaryRecord> lines) {
    }

    /** Historique d'un utilisateur quelconque. Reserve aux administrateurs. */
    @Transactional(readOnly = true)
    public List<SalaryRecord> findSalaryRecordsOfUser(Long userId) {
        return salaryRecordRepository.findByUserIdWithDetails(userId);
    }

    private SalaryRecord buildRecord(Tontine tontine,
                                     TontineMember member,
                                     YearMonth month,
                                     int periodIndex,
                                     LocalDate periodStart,
                                     TontineMember beneficiary,
                                     BigDecimal potAmount,
                                     Contribution contribution) {
        User user = member.getUser();
        BigDecimal received = member.equals(beneficiary) ? potAmount : BigDecimal.ZERO;

        SalaryCalculator.SimulatedSalary simulated = salaryCalculator.calculate(
                user.getBaseSalary(), contribution.getAmount(), received);

        return new SalaryRecord(user, tontine, month, periodIndex, periodStart,
                simulated.baseSalary(),
                simulated.tontineDeduction(),
                simulated.tontineReceived(),
                simulated.finalSalary());
    }

    /**
     * Le calcul des salaires s'appuie sur les cotisations reellement enregistrees :
     * elles doivent exister pour chaque participant avant d'être deduites.
     */
    private void requireContributionsForAllMembers(Tontine tontine,
                                                   int periodIndex,
                                                   List<Contribution> contributions) {
        if (contributions.isEmpty()) {
            throw new BusinessRuleException(
                    "Les cotisations du tour %d doivent être générées avant les salaires."
                            .formatted(periodIndex));
        }
        if (contributions.size() != tontine.getMembers().size()) {
            throw new BusinessRuleException(
                    "Cotisations incomplètes pour le tour %d : %d enregistrées pour %d participants."
                            .formatted(periodIndex, contributions.size(), tontine.getMembers().size()));
        }
    }

    /** Le cycle se terminé après le dernier tour : la tontine passe alors a COMPLETED. */
    private void completeCycleIfLastPeriod(Tontine tontine, int periodIndex) {
        if (!cycleService.isLastPeriodOfCycle(tontine, periodIndex)) {
            return;
        }
        tontine.setStatus(TontineStatus.COMPLETED);
        tontineRepository.save(tontine);
        auditService.record(currentUserProvider.findAuditAuthor(), AuditAction.TONTINE_COMPLETED,
                AUDIT_ENTITY, tontine.getId(),
                "Cycle terminé après le tour %d".formatted(periodIndex));
    }

    private void requireActive(Tontine tontine) {
        if (!tontine.isActive()) {
            throw new BusinessRuleException(
                    "Seule une tontine ACTIVE peut générer des salaires (statut actuel : %s)."
                            .formatted(tontine.getStatus()));
        }
    }
}
