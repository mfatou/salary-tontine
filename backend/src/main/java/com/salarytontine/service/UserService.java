package com.salarytontine.service;

import com.salarytontine.dto.request.ApproveUserRequest;
import com.salarytontine.dto.request.ChangePasswordRequest;
import com.salarytontine.dto.request.UpdateRoleRequest;
import com.salarytontine.dto.request.UpdateSalaryRequest;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.Role;
import com.salarytontine.enums.UserStatus;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.UnauthorizedOperationException;
import com.salarytontine.exception.ResourceNotFoundException;
import com.salarytontine.repository.UserRepository;
import com.salarytontine.security.CurrentUserProvider;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultation des utilisateurs et administration de leur role et de leur
 * salaire fictif. Ces deux attributs ne sont modifiables que par un ADMIN.
 */
@Service
public class UserService {

    private static final String AUDIT_ENTITY = "User";
    private static final String USER_RESOURCE = "Utilisateur";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ContributionCapacityService capacityService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       ContributionCapacityService capacityService,
                       CurrentUserProvider currentUserProvider,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.capacityService = capacityService;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    /**
     * Valide une inscription et attribue le rôle. Le mot de passe reste celui
     * choisi par l'employé : un administrateur capable de le fixer pourrait se
     * faire passer pour lui.
     */
    @Transactional
    public User approve(Long userId, ApproveUserRequest request) {
        User target = findById(userId);

        if (target.getStatus() == UserStatus.ACTIVE) {
            throw new BusinessRuleException("Ce compte est déjà validé.");
        }

        target.setStatus(UserStatus.ACTIVE);
        target.setRole(request.role());

        // Un administrateur n'est pas salarié : accepter un salaire ici
        // contredirait le refus oppose ensuite par updateBaseSalary.
        if (!request.role().participatesInTontines()) {
            target.setBaseSalary(BigDecimal.ZERO);
        } else if (request.baseSalary() != null) {
            target.setBaseSalary(request.baseSalary());
        }
        User saved = userRepository.save(target);

        auditService.record(currentUserProvider.requireUser(), AuditAction.USER_APPROVED,
                AUDIT_ENTITY, saved.getId(),
                "Inscription de %s validée avec le rôle %s".formatted(saved.getEmail(), saved.getRole()));
        return saved;
    }

    /** Refuse une inscription. Le compte est conservé pour la traçabilité. */
    @Transactional
    public User reject(Long userId) {
        User target = findById(userId);
        User author = currentUserProvider.requireUser();

        if (Objects.equals(author.getId(), target.getId())) {
            throw new BusinessRuleException("Un administrateur ne peut pas refuser son propre compte.");
        }
        if (target.getStatus() == UserStatus.REJECTED) {
            throw new BusinessRuleException("Cette inscription est déjà refusée.");
        }

        target.setStatus(UserStatus.REJECTED);
        User saved = userRepository.save(target);

        auditService.record(author, AuditAction.USER_REJECTED, AUDIT_ENTITY, saved.getId(),
                "Inscription de %s refusée".formatted(saved.getEmail()));
        return saved;
    }

    /** Le mot de passe actuel est exigé : un jeton volé ne suffit pas à
     *  verrouiller le compte de sa victime. */
    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        User user = currentUserProvider.requireUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedOperationException("Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Le nouveau mot de passe doit différer de l'actuel.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Seul le fait est tracé : jamais le mot de passe, ni son empreinte.
        auditService.record(user, AuditAction.USER_PASSWORD_CHANGED, AUDIT_ENTITY, user.getId(),
                "Mot de passe modifié par son propriétaire");
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAllByOrderByNameAsc();
    }

    /**
     * Comptes réellement salariés, c'est-à-dire tout le monde sauf les
     * administrateurs. C'est la population de l'annuaire salarial et le vivier
     * des participants aux tontines.
     */
    @Transactional(readOnly = true)
    public List<User> findSalaried() {
        return userRepository.findAllByOrderByNameAsc().stream()
                .filter(user -> user.getRole().participatesInTontines())
                .toList();
    }

    @Transactional(readOnly = true)
    public User findById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of(USER_RESOURCE, userId));
    }

    @Transactional
    public User updateRole(Long userId, UpdateRoleRequest request) {
        User target = findById(userId);
        User author = currentUserProvider.requireUser();

        // Empeche un administrateur de se retirer lui-même ses privilèges
        // et de rendre l'application inadministrable.
        if (Objects.equals(author.getId(), target.getId()) && request.role() != Role.ADMIN) {
            throw new BusinessRuleException("Un administrateur ne peut pas modifier son propre role.");
        }

        Role previousRole = target.getRole();

        // Devenir administrateur, c'est quitter la paie. On refuse la promotion
        // tant qu'un engagement de tontine court : le cycle deviendrait bancal,
        // avec un participant sans salaire de base.
        if (!request.role().participatesInTontines()
                && capacityService.engagedAmount(target.getId()).signum() > 0) {
            throw new BusinessRuleException(
                    "%s participe à une tontine en cours : terminez ou annulez son engagement avant "
                            + "d'en faire un administrateur.".formatted(target.getName()));
        }

        target.setRole(request.role());
        if (!request.role().participatesInTontines()) {
            target.setBaseSalary(BigDecimal.ZERO);
        }
        User saved = userRepository.save(target);

        auditService.record(author, AuditAction.USER_ROLE_UPDATED, AUDIT_ENTITY, saved.getId(),
                "Role modifié de %s vers %s".formatted(previousRole, saved.getRole()));
        return saved;
    }

    /**
     * Fixe le salaire de base d'un employé.
     *
     * <p>Nul ne fixe le sien : celui qui prépare la paie ne peut pas décider de
     * sa propre rémunération. C'est le principe de séparation des tâches, et il
     * vaut pour tous les rôles — le restreindre au comptable déplacerait
     * simplement la faille vers l'administrateur.</p>
     */
    @Transactional
    public User updateBaseSalary(Long userId, UpdateSalaryRequest request) {
        User target = findById(userId);
        User author = currentUserProvider.requireUser();

        if (Objects.equals(author.getId(), target.getId())) {
            throw new BusinessRuleException(
                    "Vous ne pouvez pas fixer votre propre salaire de base. Demandez à un autre "
                            + "responsable habilité de le faire.");
        }
        if (!target.getRole().participatesInTontines()) {
            throw new BusinessRuleException(
                    "%s est administrateur : ce rôle n'est pas salarié et n'a pas de salaire de base."
                            .formatted(target.getName()));
        }

        BigDecimal previousSalary = target.getBaseSalary();
        target.setBaseSalary(request.baseSalary());
        User saved = userRepository.save(target);

        auditService.record(author, AuditAction.USER_SALARY_UPDATED, AUDIT_ENTITY, saved.getId(),
                "Salaire de base modifié de %s vers %s".formatted(previousSalary, saved.getBaseSalary()));
        return saved;
    }
}
