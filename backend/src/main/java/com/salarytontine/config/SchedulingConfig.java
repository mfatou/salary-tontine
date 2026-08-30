package com.salarytontine.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active la planification, sauf lorsque {@code app.scheduling.enabled=false}.
 * Les tests d'integration la desactivent pour rester maitres de l'horloge.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
