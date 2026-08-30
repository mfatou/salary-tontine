package com.salarytontine.config;

import com.salarytontine.service.MonthlyRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenche le traitement des tontines actives. Le passage est quotidien et non
 * mensuel : un serveur arrêté le jour prévu perdrait sinon le tour. Un tour déjà
 * traité est ignoré.
 */
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class MonthlyRunScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonthlyRunScheduler.class);

    private final MonthlyRunService monthlyRunService;

    public MonthlyRunScheduler(MonthlyRunService monthlyRunService,
                               @Value("${app.scheduling.monthly-run-cron:0 0 2 * * *}") String cron) {
        this.monthlyRunService = monthlyRunService;
        log.info("Traitement mensuel automatique actif (cron : {}).", cron);
    }

    @Scheduled(cron = "${app.scheduling.monthly-run-cron:0 0 2 * * *}", zone = "UTC")
    public void runMonthlyGeneration() {
        monthlyRunService.runCurrentMonth();
    }
}
