package com.salarytontine.service;

import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.User;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.repository.TontineRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Capacité de cotisation : le cumul des tontines est permis tant que la somme
 * des coûts mensuels ne dépasse pas le salaire de base. Les tontines encore
 * ouvertes comptent, la place étant réservée dès l'acceptation.
 */
@Service
public class ContributionCapacityService {

    /** Statuts qui engagent reellement l'employé sur une cotisation mensuelle. */
    private static final List<TontineStatus> ENGAGING_STATUSES =
            List.of(TontineStatus.DRAFT, TontineStatus.ACTIVE);

    private final TontineRepository tontineRepository;

    public ContributionCapacityService(TontineRepository tontineRepository) {
        this.tontineRepository = tontineRepository;
    }

    /** Cotisations mensuelles déjà engagees par l'employé. */
    @Transactional(readOnly = true)
    public BigDecimal engagedAmount(Long userId) {
        BigDecimal engaged = tontineRepository.sumMonthlyCommitments(userId, ENGAGING_STATUSES);
        return engaged == null ? BigDecimal.ZERO : engaged;
    }

    /**
     * Verifie qu'une cotisation supplementaire tient dans le salaire de base.
     *
     * @param additionalMonthlyAmount cotisation de la tontine visee, ou zero
     *                                lorsqu'on revalide un engagement existant
     */
    @Transactional(readOnly = true)
    public void requireCapacity(User user, BigDecimal additionalMonthlyAmount, boolean selfService) {
        if (!user.getRole().participatesInTontines()) {
            throw new BusinessRuleException(selfService
                    ? "Un administrateur n'est pas salarié de l'entreprise : il ne participe pas aux tontines."
                    : "%s est administrateur : ce rôle n'a pas de salaire et ne cotise pas."
                            .formatted(user.getName()));
        }

        BigDecimal baseSalary = user.getBaseSalary();

        if (baseSalary == null || baseSalary.signum() <= 0) {
            throw new BusinessRuleException(selfService
                    ? "Votre salaire de base n'est pas renseigne : demandez au comptable de le définir "
                            + "avant de rejoindre une tontine."
                    : "%s n'a pas de salaire de base défini : le comptable doit le renseigner."
                            .formatted(user.getName()));
        }

        BigDecimal total = engagedAmount(user.getId()).add(
                additionalMonthlyAmount == null ? BigDecimal.ZERO : additionalMonthlyAmount);

        if (total.compareTo(baseSalary) > 0) {
            throw new BusinessRuleException(selfService
                    ? ("Vos cotisations mensuelles atteindraient %s pour un salaire de base de %s. "
                            + "Une tontine ne peut pas prélever plus que le salaire.")
                            .formatted(total, baseSalary)
                    : ("Les cotisations de %s atteindraient %s pour un salaire de base de %s : "
                            + "le prélèvement dépasserait son salaire.")
                            .formatted(user.getName(), total, baseSalary));
        }
    }
}
