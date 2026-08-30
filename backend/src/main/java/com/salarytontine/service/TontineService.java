package com.salarytontine.service;

import com.salarytontine.dto.request.AddMemberRequest;
import com.salarytontine.dto.request.CreateTontineRequest;
import com.salarytontine.dto.request.UpdateTontineRequest;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineMember;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.exception.ResourceNotFoundException;
import com.salarytontine.exception.UnauthorizedOperationException;
import com.salarytontine.repository.TontineJoinRequestRepository;
import com.salarytontine.repository.TontineMemberRepository;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.repository.UserRepository;
import com.salarytontine.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cycle de vie d'une tontine : création, composition et activation.
 * Une tontine est modifiable tant qu'elle est au statut DRAFT ; une fois ACTIVE,
 * son montant, sa date de début et sa composition sont figes.
 */
@Service
public class TontineService {

    /** Nombre minimal de participants pour qu'une tontine puisse être activée. */
    public static final int MINIMUM_MEMBERS_FOR_ACTIVATION = 2;

    private static final String AUDIT_ENTITY = "Tontine";
    private static final String TONTINE_RESOURCE = "Tontine";
    private static final String USER_RESOURCE = "Utilisateur";
    private static final String IMMUTABLE_MESSAGE =
            "Seule une tontine au statut DRAFT peut être modifiée.";

    private final TontineRepository tontineRepository;
    private final TontineMemberRepository memberRepository;
    private final TontineJoinRequestRepository joinRequestRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;
    private final TontineCycleService cycleService;
    private final ContributionCapacityService capacityService;
    private final AuditService auditService;

    public TontineService(TontineRepository tontineRepository,
                          TontineMemberRepository memberRepository,
                          TontineJoinRequestRepository joinRequestRepository,
                          UserRepository userRepository,
                          CurrentUserProvider currentUserProvider,
                          TontineCycleService cycleService,
                          ContributionCapacityService capacityService,
                          AuditService auditService) {
        this.tontineRepository = tontineRepository;
        this.memberRepository = memberRepository;
        this.joinRequestRepository = joinRequestRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
        this.cycleService = cycleService;
        this.capacityService = capacityService;
        this.auditService = auditService;
    }

    /**
     * Une cadence libre exige sa durée : sans elle, aucun tour ne peut être
     * borné et le cycle n'existe pas.
     */
    private void requireConsistentFrequency(Tontine tontine) {
        if (tontine.getFrequency().requiresExplicitLength() && tontine.getPeriodDays() == null) {
            throw new BusinessRuleException(
                    "Une cadence personnalisée exige une durée de tour, en jours.");
        }
    }

    @Transactional
    public Tontine create(CreateTontineRequest request) {
        User author = currentUserProvider.requireUser();
        Tontine tontine = new Tontine(
                request.name().trim(),
                request.monthlyAmount(),
                normalizeToFirstDayOfMonth(request),
                author,
                request.targetMemberCount(),
                request.frequency(),
                request.periodDays());

        requireConsistentFrequency(tontine);

        Tontine saved = tontineRepository.save(tontine);
        auditService.record(author, AuditAction.TONTINE_CREATED, AUDIT_ENTITY, saved.getId(),
                "Création de la tontine '%s' (%s / mois, début %s)"
                        .formatted(saved.getName(), saved.getMonthlyAmount(), saved.getStartMonth()));
        return saved;
    }

    @Transactional(readOnly = true)
    public Tontine findByIdWithMembers(Long tontineId) {
        return tontineRepository.findByIdWithMembers(tontineId)
                .orElseThrow(() -> ResourceNotFoundException.of(TONTINE_RESOURCE, tontineId));
    }

    /** Liste visible par l'utilisateur : toutes pour un gestionnaire, les siennes sinon. */
    @Transactional(readOnly = true)
    public List<Tontine> findVisibleTontines() {
        if (currentUserProvider.hasManagementPrivileges()) {
            return tontineRepository.findAllWithDetails();
        }
        return tontineRepository.findAllByMemberUserId(currentUserProvider.requireUserId());
    }

    /**
     * Verifie que l'utilisateur courant peut consulter la tontine.
     * Un employé n'y a accès que s'il en est participant.
     */
    @Transactional(readOnly = true)
    public void checkReadAccess(Tontine tontine) {
        if (currentUserProvider.hasManagementPrivileges()) {
            return;
        }
        // Une tontine encore au statut DRAFT est ouverte aux inscriptions :
        // tout employé doit pouvoir l'examiner avant de demander a la rejoindre.
        if (tontine.isDraft()) {
            return;
        }
        Long userId = currentUserProvider.requireUserId();
        boolean isMember = tontine.getMembers().stream()
                .anyMatch(member -> member.getUser().getId().equals(userId));
        if (!isMember) {
            throw new UnauthorizedOperationException(
                    "Vous n'etes pas participant de cette tontine.");
        }
    }

    /** Tontines ouvertes aux inscriptions, proposees a tous les employés. */
    @Transactional(readOnly = true)
    public List<Tontine> findOpenTontines() {
        return tontineRepository.findAllByStatusWithDetails(TontineStatus.DRAFT);
    }

    /**
     * Prochaine place libre dans l'ordre de passage.
     * Sert a placer automatiquement un employé dont la demande vient d'être
     * acceptee, sans imposer au comptable de choisir un rang a la main.
     */
    public int nextAvailableTurnOrder(Tontine tontine) {
        return tontine.getMembers().stream()
                .map(TontineMember::getTurnOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    @Transactional
    public Tontine update(Long tontineId, UpdateTontineRequest request) {
        Tontine tontine = findByIdWithMembers(tontineId);
        requireDraft(tontine);

        if (request.name() != null) {
            tontine.setName(request.name().trim());
        }
        if (request.monthlyAmount() != null) {
            tontine.setMonthlyAmount(request.monthlyAmount());
        }
        if (request.startDate() != null) {
            tontine.setStartDate(request.startDate().withDayOfMonth(1));
        }
        if (request.targetMemberCount() != null) {
            if (request.targetMemberCount() < tontine.getMembers().size()) {
                throw new BusinessRuleException(
                        "La tontine compte déjà %d participants : le nombre de places ne peut pas être inferieur."
                                .formatted(tontine.getMembers().size()));
            }
            tontine.setTargetMemberCount(request.targetMemberCount());
        }
        if (request.frequency() != null) {
            tontine.setFrequency(request.frequency());
            if (request.frequency().requiresExplicitLength()) {
                tontine.setPeriodDays(request.periodDays());
            }
        } else if (request.periodDays() != null
                && tontine.getFrequency().requiresExplicitLength()) {
            tontine.setPeriodDays(request.periodDays());
        }
        requireConsistentFrequency(tontine);

        Tontine saved = tontineRepository.save(tontine);
        auditService.record(currentUserProvider.requireUser(), AuditAction.TONTINE_UPDATED,
                AUDIT_ENTITY, saved.getId(),
                "Modification de la tontine '%s'".formatted(saved.getName()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TontineMember> findMembers(Long tontineId) {
        Tontine tontine = findByIdWithMembers(tontineId);
        checkReadAccess(tontine);
        return memberRepository.findByTontineIdWithUser(tontine.getId());
    }

    @Transactional
    public TontineMember addMember(Long tontineId, AddMemberRequest request) {
        Tontine tontine = findByIdWithMembers(tontineId);
        requireDraft(tontine);

        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> ResourceNotFoundException.of(USER_RESOURCE, request.userId()));

        // Même séparation qu'à l'acceptation d'une demande : on ne s'inscrit pas
        // soi-même dans une tontine que l'on administre, ordre de passage compris.
        if (user.getId().equals(currentUserProvider.requireUserId())) {
            throw new UnauthorizedOperationException(
                    "Vous ne pouvez pas vous ajouter vous-même : demandez à rejoindre la tontine, "
                            + "un autre responsable arbitrera votre demande.");
        }

        if (memberRepository.existsByTontineIdAndUserId(tontine.getId(), user.getId())) {
            throw new DuplicateResourceException(
                    "%s participe déjà a cette tontine.".formatted(user.getName()));
        }
        memberRepository.findByTontineIdAndTurnOrder(tontine.getId(), request.turnOrder())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException(
                            "L'ordre de passage %d est déjà attribue a %s."
                                    .formatted(request.turnOrder(), existing.getUser().getName()));
                });
        // Participer a plusieurs tontines est permis, tant que le cumul des
        // cotisations tient dans le salaire de base.
        capacityService.requireCapacity(user, tontine.monthlyCost(), false);

        TontineMember member = memberRepository.save(new TontineMember(tontine, user, request.turnOrder()));
        auditService.record(currentUserProvider.requireUser(), AuditAction.MEMBER_ADDED,
                AUDIT_ENTITY, tontine.getId(),
                "Ajout de %s avec l'ordre de passage %d".formatted(user.getName(), request.turnOrder()));
        return member;
    }

    /** Retrait décide par le comptable. La tontine doit encore être ouverte. */
    @Transactional
    public void removeMember(Long tontineId, Long userId) {
        Tontine tontine = findByIdWithMembers(tontineId);
        requireDraft(tontine);

        TontineMember member = findMembership(tontine, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cet utilisateur ne participe pas a la tontine %d.".formatted(tontineId)));

        detachMember(tontine, member, AuditAction.MEMBER_REMOVED,
                "Retrait de %s".formatted(member.getUser().getName()));
    }

    /**
     * Depart volontaire d'un participant.
     *
     * <p>Il n'est possible que tant que la tontine n'a pas demarre. Un cycle
     * engage ne se quitte pas : celui qui a déjà reçu la cagnotte doit encore
     * ses cotisations, et celui qui n'a pas encore reçu perdrait ce qu'il a
     * verse tout en modifiant la cagnotte des autres. La seule issue est alors
     * l'annulation de la tontine entiere par le comptable.</p>
     */
    @Transactional
    public void leaveTontine(Long tontineId) {
        Tontine tontine = findByIdWithMembers(tontineId);
        Long userId = currentUserProvider.requireUserId();

        TontineMember member = findMembership(tontine, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vous ne participez pas a cette tontine."));

        requireLeavable(tontine, member);
        detachMember(tontine, member, AuditAction.MEMBER_LEFT,
                "Depart volontaire de %s".formatted(member.getUser().getName()));
    }

    /**
     * Annule une tontine : le cycle s'arrête, plus aucune génération n'est
     * possible. Les mois déjà générés restent dans l'historique salarial ;
     * l'annulation interrompt le cycle, elle ne le reecrit pas.
     */
    @Transactional
    public Tontine cancel(Long tontineId) {
        Tontine tontine = findByIdWithMembers(tontineId);

        if (tontine.getStatus() == TontineStatus.COMPLETED
                || tontine.getStatus() == TontineStatus.CANCELLED) {
            throw new BusinessRuleException(
                    "Cette tontine est déjà close (statut %s).".formatted(tontine.getStatus()));
        }

        TontineStatus previousStatus = tontine.getStatus();
        tontine.setStatus(TontineStatus.CANCELLED);
        Tontine saved = tontineRepository.save(tontine);

        auditService.record(currentUserProvider.findAuditAuthor(), AuditAction.TONTINE_CANCELLED,
                AUDIT_ENTITY, saved.getId(),
                "Annulation de '%s' depuis le statut %s".formatted(saved.getName(), previousStatus));
        return saved;
    }

    /**
     * Suppression definitive, réservée a une tontine encore au statut DRAFT.
     *
     * <p>Des qu'un cycle a demarre, des cotisations et des salaires simules y
     * sont rattaches, et la suppression en cascade les effacerait. Une tontine
     * demarree s'annulé, elle ne se supprimé pas.</p>
     */
    @Transactional
    public void delete(Long tontineId) {
        Tontine tontine = findByIdWithMembers(tontineId);

        if (!tontine.isDraft()) {
            throw new BusinessRuleException(
                    "Seule une tontine encore ouverte peut être supprimée. Celle-ci a demarre : "
                            + "annulez-la pour arrêter le cycle sans effacer l'historique salarial.");
        }

        String name = tontine.getName();
        joinRequestRepository.deleteAll(joinRequestRepository.findByTontineIdWithDetails(tontineId));
        tontineRepository.delete(tontine);

        auditService.record(currentUserProvider.findAuditAuthor(), AuditAction.TONTINE_DELETED,
                AUDIT_ENTITY, tontineId, "Suppression de la tontine '%s'".formatted(name));
    }

    private java.util.Optional<TontineMember> findMembership(Tontine tontine, Long userId) {
        return tontine.getMembers().stream()
                .filter(member -> member.getUser().getId().equals(userId))
                .findFirst();
    }

    private void requireLeavable(Tontine tontine, TontineMember member) {
        switch (tontine.getStatus()) {
            case DRAFT -> {
                // Rien n'a encore été verse ni reçu : le depart est sans consequence.
            }
            case ACTIVE -> throw new BusinessRuleException(activeDepartureMessage(tontine, member));
            case COMPLETED -> throw new BusinessRuleException(
                    "Le cycle est terminé : il n'y a plus rien a quitter.");
            case CANCELLED -> throw new BusinessRuleException(
                    "Cette tontine est annulée : vous n'y participez plus.");
        }
    }

    /** Explique pourquoi le départ est refusé, selon que le tour est passé ou non. */
    private String activeDepartureMessage(Tontine tontine, TontineMember member) {
        LocalDate myTurn = tontine.periodStart(member.getTurnOrder());
        LocalDate cycleEnd = cycleService.lastPeriodEnd(tontine);

        if (!myTurn.isAfter(LocalDate.now())) {
            return ("Vous avez déjà reçu la cagnotte de %s le %s. Vos cotisations restent dues jusqu'à la fin "
                    + "du cycle (%s) : partir maintenant reviendrait à conserver l'argent des autres participants.")
                    .formatted(tontine.calculatePotAmount(), myTurn, cycleEnd);
        }
        return ("Une tontine démarrée ne peut pas être quittée : votre tour est prévu le %s. Partir maintenant "
                + "vous ferait perdre les cotisations déjà versées et réduirait la cagnotte de tous les autres "
                + "participants. Seul le comptable peut annuler la tontine entière.")
                .formatted(myTurn);
    }

    private void detachMember(Tontine tontine, TontineMember member, AuditAction action, String details) {
        Long userId = member.getUser().getId();

        tontine.removeMember(member);
        tontineRepository.saveAndFlush(tontine);
        compactTurnOrders(tontine.getId());

        // La demande acceptee disparait avec la participation, ce qui laisse
        // l'employé libre d'en soumettre une nouvelle plus tard.
        joinRequestRepository.findByTontineIdAndUserId(tontine.getId(), userId)
                .ifPresent(joinRequestRepository::delete);

        auditService.record(currentUserProvider.findAuditAuthor(), action,
                AUDIT_ENTITY, tontine.getId(), details);
    }

    /**
     * Renumerote les participants de 1 a n après un depart.
     *
     * <p>Un trou dans l'ordre de passage casserait le cycle : le mois n cherche
     * le participant dont l'ordre vaut exactement n. La renumerotation avance
     * par ordre croissant, une instruction a la fois, car la place visee n'est
     * liberee que par le deplacement précédent.</p>
     */
    private void compactTurnOrders(Long tontineId) {
        int expected = 1;
        for (TontineMember member : memberRepository.findByTontineIdWithUser(tontineId)) {
            if (!Integer.valueOf(expected).equals(member.getTurnOrder())) {
                member.setTurnOrder(expected);
                memberRepository.saveAndFlush(member);
            }
            expected++;
        }
    }

    @Transactional
    public Tontine activate(Long tontineId) {
        Tontine tontine = findByIdWithMembers(tontineId);
        requireDraft(tontine);
        checkActivationPreconditions(tontine);

        // La durée annoncee s'aligne sur la composition réelle : le nombre de
        // places declare n'a plus de sens une fois la liste figée.
        tontine.setTargetMemberCount(tontine.getMembers().size());
        tontine.setStatus(TontineStatus.ACTIVE);
        Tontine saved = tontineRepository.save(tontine);

        auditService.record(currentUserProvider.requireUser(), AuditAction.TONTINE_ACTIVATED,
                AUDIT_ENTITY, saved.getId(),
                "Activation de '%s' avec %d participants, cadence %s, cycle du %s au %s"
                        .formatted(saved.getName(), saved.getMembers().size(), saved.getFrequency(),
                                saved.getStartDate(), cycleService.lastPeriodEnd(saved)));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<TontineCycleService.CycleSlot> buildSchedule(Long tontineId) {
        Tontine tontine = findByIdWithMembers(tontineId);
        checkReadAccess(tontine);
        return cycleService.buildSchedule(tontine);
    }

    private void checkActivationPreconditions(Tontine tontine) {
        List<TontineMember> members = tontine.getMembers();

        if (members.size() < MINIMUM_MEMBERS_FOR_ACTIVATION) {
            throw new BusinessRuleException(
                    "Une tontine doit compter au moins %d participants pour être activée."
                            .formatted(MINIMUM_MEMBERS_FOR_ACTIVATION));
        }

        boolean hasMissingTurnOrder = members.stream().anyMatch(member -> member.getTurnOrder() == null);
        if (hasMissingTurnOrder) {
            throw new BusinessRuleException("Tous les participants doivent avoir un ordre de passage.");
        }

        // Les ordres doivent former la suite complete 1..n : le mois k cherche
        // le participant d'ordre k, un trou rendrait ce mois insoluble.
        Set<Integer> turnOrders = members.stream()
                .map(TontineMember::getTurnOrder)
                .collect(Collectors.toSet());
        Set<Integer> expectedOrders = IntStream.rangeClosed(1, members.size())
                .boxed()
                .collect(Collectors.toSet());
        if (!turnOrders.equals(expectedOrders)) {
            throw new BusinessRuleException(
                    "Les ordres de passage doivent former une suite complete de 1 a %d, sans doublon ni trou."
                            .formatted(members.size()));
        }

        // La cotisation de cette tontine est déjà comptee dans l'engagement de
        // chaque participant : on revalide sans rien ajouter.
        for (TontineMember member : members) {
            capacityService.requireCapacity(member.getUser(), BigDecimal.ZERO, false);
        }
    }

    private void requireDraft(Tontine tontine) {
        if (!tontine.isDraft()) {
            throw new BusinessRuleException(IMMUTABLE_MESSAGE);
        }
    }

    /** La date de début représente toujours le premier jour du mois de depart. */
    private java.time.LocalDate normalizeToFirstDayOfMonth(CreateTontineRequest request) {
        return request.startDate().withDayOfMonth(1);
    }
}
