package com.salarytontine.config;

import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineJoinRequest;
import com.salarytontine.entity.TontineMember;
import com.salarytontine.entity.User;
import com.salarytontine.enums.Role;
import com.salarytontine.repository.TontineJoinRequestRepository;
import com.salarytontine.repository.TontineMemberRepository;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.repository.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Jeu de données fictives destine au developpement et a la demonstration.
 *
 * <p>Actif uniquement lorsque {@code APP_SEED_ENABLED=true}. Le mot de passe des
 * comptes provient de {@code APP_SEED_PASSWORD} : aucun mot de passe n'est ecrit
 * en dur dans le code. Toutes les personnes et tous les montants sont inventes.</p>
 */
@Configuration
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String DEMO_TONTINE_NAME = "Tontine Equipe A";
    private static final BigDecimal DEMO_MONTHLY_AMOUNT = new BigDecimal("50000");
    private static final int MINIMUM_SEED_PASSWORD_LENGTH = 8;

    /**
     * Les premiers employés sont déjà acceptes dans la tontine ; les suivants
     * arrivent avec une demande en attente, pour que le comptable ait quelque
     * chose a arbitrer des sa première connexion.
     */
    private static final int DEMO_ACCEPTED_MEMBERS = 3;

    /** Employés fictifs et leurs salaires simules. */
    private static final List<DemoEmployee> DEMO_EMPLOYEES = List.of(
            new DemoEmployee("Awa Ndiaye", "awa@salarytontine.test", new BigDecimal("500000")),
            new DemoEmployee("Fatou Fall", "fatou@salarytontine.test", new BigDecimal("450000")),
            new DemoEmployee("Mamadou Diop", "mamadou@salarytontine.test", new BigDecimal("600000")),
            new DemoEmployee("Khady Sarr", "khady@salarytontine.test", new BigDecimal("550000")),
            new DemoEmployee("Aliou Ba", "aliou@salarytontine.test", new BigDecimal("400000")));

    @Bean
    public ApplicationRunner seedDemoData(AppProperties properties,
                                          UserRepository userRepository,
                                          TontineRepository tontineRepository,
                                          TontineMemberRepository memberRepository,
                                          TontineJoinRequestRepository joinRequestRepository,
                                          PasswordEncoder passwordEncoder) {
        return args -> seed(properties, userRepository, tontineRepository, memberRepository,
                joinRequestRepository, passwordEncoder);
    }

    @Transactional
    void seed(AppProperties properties,
              UserRepository userRepository,
              TontineRepository tontineRepository,
              TontineMemberRepository memberRepository,
              TontineJoinRequestRepository joinRequestRepository,
              PasswordEncoder passwordEncoder) {

        String seedPassword = properties.getSeed().getPassword();
        if (seedPassword == null || seedPassword.length() < MINIMUM_SEED_PASSWORD_LENGTH) {
            log.warn("Seed ignore : APP_SEED_PASSWORD est absent ou trop court ({} caracteres minimum).",
                    MINIMUM_SEED_PASSWORD_LENGTH);
            return;
        }
        if (userRepository.count() > 0) {
            log.info("Seed ignore : la base contient déjà des utilisateurs.");
            return;
        }

        String passwordHash = passwordEncoder.encode(seedPassword);

        User accountant = userRepository.save(new User("Comptable Demo", "comptable@salarytontine.test",
                passwordHash, Role.ACCOUNTANT, BigDecimal.ZERO));
        userRepository.save(new User("Admin Demo", "admin@salarytontine.test",
                passwordHash, Role.ADMIN, BigDecimal.ZERO));

        List<User> employees = DEMO_EMPLOYEES.stream()
                .map(employee -> userRepository.save(new User(employee.name(), employee.email(),
                        passwordHash, Role.EMPLOYEE, employee.baseSalary())))
                .toList();

        seedDemoTontine(tontineRepository, memberRepository, joinRequestRepository, accountant, employees);

        log.info("Seed terminé : {} comptes créés, tontine '{}' au statut DRAFT "
                        + "({} participants acceptes, {} demandes en attente).",
                employees.size() + 2, DEMO_TONTINE_NAME, DEMO_ACCEPTED_MEMBERS,
                employees.size() - DEMO_ACCEPTED_MEMBERS);
    }

    /**
     * La tontine de demonstration reste au statut DRAFT afin que le parcours
     * d'activation puisse être joue de bout en bout.
     */
    private void seedDemoTontine(TontineRepository tontineRepository,
                                 TontineMemberRepository memberRepository,
                                 TontineJoinRequestRepository joinRequestRepository,
                                 User accountant,
                                 List<User> employees) {
        LocalDate startDate = YearMonth.now().atDay(1);
        Tontine tontine = tontineRepository.save(
                new Tontine(DEMO_TONTINE_NAME, DEMO_MONTHLY_AMOUNT, startDate, accountant));

        for (int index = 0; index < employees.size(); index++) {
            User employee = employees.get(index);
            if (index < DEMO_ACCEPTED_MEMBERS) {
                memberRepository.save(new TontineMember(tontine, employee, index + 1));
            } else {
                joinRequestRepository.save(new TontineJoinRequest(tontine, employee,
                        "Je souhaite rejoindre le cycle qui demarre en %s.".formatted(YearMonth.from(startDate))));
            }
        }
    }

    private record DemoEmployee(String name, String email, BigDecimal baseSalary) {
    }
}
