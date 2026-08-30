package com.salarytontine.service;

import com.salarytontine.entity.Tontine;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.repository.ContributionRepository;
import com.salarytontine.repository.SalaryRecordRepository;
import com.salarytontine.repository.TontineRepository;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Traitement des tontines actives, rejouable sans risque. Volontairement non
 * transactionnelle : une transaction englobante annulerait tout le lot dès
 * qu'une seule tontine échouerait.
 */
@Service
public class MonthlyRunService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyRunService.class);

    private final TontineRepository tontineRepository;
    private final ContributionRepository contributionRepository;
    private final SalaryRecordRepository salaryRecordRepository;
    private final ContributionService contributionService;
    private final SalaryService salaryService;
    private final TontineCycleService cycleService;

    public MonthlyRunService(TontineRepository tontineRepository,
                             ContributionRepository contributionRepository,
                             SalaryRecordRepository salaryRecordRepository,
                             ContributionService contributionService,
                             SalaryService salaryService,
                             TontineCycleService cycleService) {
        this.tontineRepository = tontineRepository;
        this.contributionRepository = contributionRepository;
        this.salaryRecordRepository = salaryRecordRepository;
        this.contributionService = contributionService;
        this.salaryService = salaryService;
        this.cycleService = cycleService;
    }

    /** Traite tous les tours échus à ce jour. */
    public MonthlyRunReport runCurrentMonth() {
        return runFor(LocalDate.now());
    }

    /**
     * Généré cotisations puis salaires du mois demande, pour chaque tontine
     * active dont ce mois appartient au cycle et n'a pas déjà été traité.
     */
    public MonthlyRunReport runFor(LocalDate date) {
        List<Tontine> activeTontines =
                tontineRepository.findAllByStatusWithDetails(TontineStatus.ACTIVE);

        int processed = 0;
        int skipped = 0;
        int failed = 0;

        for (Tontine tontine : activeTontines) {
            // Tous les tours échus, pas seulement le dernier : un serveur arrêté
            // quelques jours rattrape ce qu'il a manqué, quelle que soit la cadence.
            List<Integer> due = cycleService.duePeriods(tontine, date);
            if (due.isEmpty()) {
                skipped++;
                continue;
            }

            boolean didSomething = false;
            for (Integer periodIndex : due) {
                try {
                    didSomething |= processPeriod(tontine.getId(), periodIndex);
                } catch (RuntimeException failure) {
                    failed++;
                    // Un tour en échec ne prive ni les suivants ni les autres
                    // tontines de leur traitement.
                    log.warn("Traitement du tour {} de la tontine {} échoué : {}",
                            periodIndex, tontine.getId(), failure.getMessage());
                    break;
                }
            }
            if (didSomething) {
                processed++;
            } else {
                skipped++;
            }
        }

        MonthlyRunReport report = new MonthlyRunReport(date, processed, skipped, failed);
        log.info("Traitement automatique du {} : {} tontine(s) traitée(s), {} ignorée(s), {} en échec.",
                date, processed, skipped, failed);
        return report;
    }

    /** @return vrai si quelque chose a réellement été généré. */
    private boolean processPeriod(Long tontineId, int periodIndex) {
        boolean didSomething = false;

        if (!contributionRepository.existsByTontineIdAndPeriodIndex(tontineId, periodIndex)) {
            contributionService.generateForPeriod(tontineId, periodIndex);
            didSomething = true;
        }
        if (!salaryRecordRepository.existsByTontineIdAndPeriodIndex(tontineId, periodIndex)) {
            salaryService.generateForPeriod(tontineId, periodIndex);
            didSomething = true;
        }
        return didSomething;
    }

    /** Bilan d'un passage du traitement mensuel. */
    public record MonthlyRunReport(LocalDate date, int processed, int skipped, int failed) {
    }
}
