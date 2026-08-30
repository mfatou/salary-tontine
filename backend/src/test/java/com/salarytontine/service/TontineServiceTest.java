package com.salarytontine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.salarytontine.support.TestEntities;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("TontineService")
class TontineServiceTest {

    private static final BigDecimal MONTHLY_AMOUNT = new BigDecimal("50000");
    private static final BigDecimal VALID_SALARY = new BigDecimal("500000");
    private static final YearMonth START = YearMonth.of(2026, 8);
    private static final Long TONTINE_ID = 10L;

    @Mock
    private TontineRepository tontineRepository;

    @Mock
    private TontineMemberRepository memberRepository;

    @Mock
    private TontineJoinRequestRepository joinRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditService auditService;

    private TontineService tontineService;
    private User manager;

    @BeforeEach
    void setUp() {
        // Le service de capacite est utilise en vrai, branche sur le depot déjà
        // simule : la règle de plafond est ainsi reellement exercee.
        tontineService = new TontineService(tontineRepository, memberRepository, joinRequestRepository,
                userRepository, currentUserProvider, new TontineCycleService(),
                new ContributionCapacityService(tontineRepository), auditService);
        manager = TestEntities.manager(1L);
    }

    private Tontine draftWithMembers(int memberCount, BigDecimal salary) {
        Tontine tontine = TestEntities.tontine(TONTINE_ID, MONTHLY_AMOUNT, START, manager);
        for (int index = 0; index < memberCount; index++) {
            User employee = TestEntities.employee(100L + index, "Employé" + index, salary);
            TestEntities.addMember(tontine, employee, index + 1, 200L + index);
        }
        return tontine;
    }

    @Nested
    @DisplayName("création")
    class Creation {

        @Test
        @DisplayName("crée une tontine au statut DRAFT")
        void createsTontineAsDraft() {
            when(currentUserProvider.requireUser()).thenReturn(manager);
            when(tontineRepository.save(any(Tontine.class))).thenAnswer(call -> call.getArgument(0));

            Tontine created = tontineService.create(new CreateTontineRequest(
                    "Tontine Equipe A", MONTHLY_AMOUNT, LocalDate.of(2026, 8, 1), null, null, null));

            assertThat(created.getStatus()).isEqualTo(TontineStatus.DRAFT);
            assertThat(created.getMonthlyAmount()).isEqualByComparingTo(MONTHLY_AMOUNT);
            assertThat(created.getCreatedBy()).isEqualTo(manager);
            verify(auditService).record(eq(manager), eq(AuditAction.TONTINE_CREATED), eq("Tontine"), any(), any());
        }

        @Test
        @DisplayName("ramene la date de début au premier jour du mois")
        void normalizesStartDateToFirstDayOfMonth() {
            when(currentUserProvider.requireUser()).thenReturn(manager);
            when(tontineRepository.save(any(Tontine.class))).thenAnswer(call -> call.getArgument(0));

            Tontine created = tontineService.create(new CreateTontineRequest(
                    "Tontine", MONTHLY_AMOUNT, LocalDate.of(2026, 8, 17), null, null, null));

            assertThat(created.getStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(created.getStartMonth()).isEqualTo(START);
        }
    }

    @Nested
    @DisplayName("gestion des participants")
    class MemberManagement {

        @Test
        @DisplayName("ajoute un participant avec son ordre de passage")
        void addsMember() {
            Tontine tontine = draftWithMembers(0, VALID_SALARY);
            User employee = TestEntities.employee(50L, "Awa", VALID_SALARY);

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(userRepository.findById(50L)).thenReturn(Optional.of(employee));
            when(memberRepository.existsByTontineIdAndUserId(TONTINE_ID, 50L)).thenReturn(false);
            when(memberRepository.findByTontineIdAndTurnOrder(TONTINE_ID, 1)).thenReturn(Optional.empty());
            when(memberRepository.save(any(TontineMember.class))).thenAnswer(call -> call.getArgument(0));
            when(currentUserProvider.requireUser()).thenReturn(manager);

            TontineMember member = tontineService.addMember(TONTINE_ID, new AddMemberRequest(50L, 1));

            assertThat(member.getUser()).isEqualTo(employee);
            assertThat(member.getTurnOrder()).isEqualTo(1);
            verify(auditService).record(eq(manager), eq(AuditAction.MEMBER_ADDED), eq("Tontine"), eq(TONTINE_ID), any());
        }

        @Test
        @DisplayName("refuse un participant déjà present dans la tontine")
        void rejectsDuplicateMember() {
            Tontine tontine = draftWithMembers(1, VALID_SALARY);
            User employee = TestEntities.employee(50L, "Awa", VALID_SALARY);

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(userRepository.findById(50L)).thenReturn(Optional.of(employee));
            when(memberRepository.existsByTontineIdAndUserId(TONTINE_ID, 50L)).thenReturn(true);

            assertThatThrownBy(() -> tontineService.addMember(TONTINE_ID, new AddMemberRequest(50L, 2)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("participe déjà a cette tontine");

            verify(memberRepository, never()).save(any(TontineMember.class));
        }

        @Test
        @DisplayName("refuse un ordre de passage déjà attribue")
        void rejectsDuplicateTurnOrder() {
            Tontine tontine = draftWithMembers(1, VALID_SALARY);
            User employee = TestEntities.employee(50L, "Awa", VALID_SALARY);
            TontineMember existing = tontine.getMembers().get(0);

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(userRepository.findById(50L)).thenReturn(Optional.of(employee));
            when(memberRepository.existsByTontineIdAndUserId(TONTINE_ID, 50L)).thenReturn(false);
            when(memberRepository.findByTontineIdAndTurnOrder(TONTINE_ID, 1)).thenReturn(Optional.of(existing));

            assertThatThrownBy(() -> tontineService.addMember(TONTINE_ID, new AddMemberRequest(50L, 1)))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("ordre de passage 1 est déjà attribue");

            verify(memberRepository, never()).save(any(TontineMember.class));
        }

        @Test
        @DisplayName("accepte un participant déjà engage ailleurs si son salaire le supporte")
        void acceptsMemberAlreadyEngagedElsewhere() {
            Tontine tontine = draftWithMembers(0, VALID_SALARY);
            User employee = TestEntities.employee(50L, "Awa", VALID_SALARY);

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(userRepository.findById(50L)).thenReturn(Optional.of(employee));
            when(memberRepository.existsByTontineIdAndUserId(TONTINE_ID, 50L)).thenReturn(false);
            when(memberRepository.findByTontineIdAndTurnOrder(TONTINE_ID, 1)).thenReturn(Optional.empty());
            // Déjà 100 000 engages ailleurs : avec 50 000 de plus, on reste
            // sous les 500 000 de salaire de base.
            when(tontineRepository.sumMonthlyCommitments(eq(50L), any()))
                    .thenReturn(new BigDecimal("100000"));
            when(memberRepository.save(any(TontineMember.class))).thenAnswer(call -> call.getArgument(0));
            when(currentUserProvider.requireUser()).thenReturn(manager);

            assertThatCode(() -> tontineService.addMember(TONTINE_ID, new AddMemberRequest(50L, 1)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("refuse un participant dont les cotisations depasseraient le salaire")
        void rejectsMemberBeyondSalaryCapacity() {
            Tontine tontine = draftWithMembers(0, VALID_SALARY);
            User employee = TestEntities.employee(50L, "Awa", VALID_SALARY);

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(userRepository.findById(50L)).thenReturn(Optional.of(employee));
            when(memberRepository.existsByTontineIdAndUserId(TONTINE_ID, 50L)).thenReturn(false);
            when(memberRepository.findByTontineIdAndTurnOrder(TONTINE_ID, 1)).thenReturn(Optional.empty());
            // 480 000 déjà engages : ajouter 50 000 dépasse le salaire de 500 000.
            when(tontineRepository.sumMonthlyCommitments(eq(50L), any()))
                    .thenReturn(new BigDecimal("480000"));

            assertThatThrownBy(() -> tontineService.addMember(TONTINE_ID, new AddMemberRequest(50L, 1)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("dépasserait son salaire");

            verify(memberRepository, never()).save(any(TontineMember.class));
        }

        @Test
        @DisplayName("refuse toute modification de composition sur une tontine ACTIVE")
        void rejectsMemberChangesOnActiveTontine() {
            Tontine active = draftWithMembers(2, VALID_SALARY);
            active.setStatus(TontineStatus.ACTIVE);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> tontineService.addMember(TONTINE_ID, new AddMemberRequest(50L, 3)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("DRAFT");

            assertThatThrownBy(() -> tontineService.removeMember(TONTINE_ID, 100L))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("retire un participant et renumerote ceux qui restent")
        void removesMemberFromDraft() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            TontineMember remaining = tontine.getMembers().get(1);
            assertThat(remaining.getTurnOrder()).isEqualTo(2);

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(memberRepository.findByTontineIdWithUser(TONTINE_ID)).thenReturn(List.of(remaining));
            when(joinRequestRepository.findByTontineIdAndUserId(TONTINE_ID, 100L)).thenReturn(Optional.empty());
            when(currentUserProvider.findAuditAuthor()).thenReturn(manager);

            tontineService.removeMember(TONTINE_ID, 100L);

            assertThat(tontine.getMembers()).hasSize(1);
            // Un trou dans l'ordre de passage rendrait un mois du cycle insoluble.
            assertThat(remaining.getTurnOrder()).isEqualTo(1);
            verify(memberRepository).saveAndFlush(remaining);
            verify(auditService).record(eq(manager), eq(AuditAction.MEMBER_REMOVED), eq("Tontine"), eq(TONTINE_ID), any());
        }

        @Test
        @DisplayName("refuse le retrait d'un utilisateur non participant")
        void rejectsRemovalOfNonMember() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));

            assertThatThrownBy(() -> tontineService.removeMember(TONTINE_ID, 999L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("depart volontaire")
    class Departure {

        @Test
        @DisplayName("autorise le depart tant que la tontine n'a pas demarre")
        void allowsLeavingOpenTontine() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            TontineMember remaining = tontine.getMembers().get(1);

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(currentUserProvider.requireUserId()).thenReturn(100L);
            when(memberRepository.findByTontineIdWithUser(TONTINE_ID)).thenReturn(List.of(remaining));
            when(joinRequestRepository.findByTontineIdAndUserId(TONTINE_ID, 100L)).thenReturn(Optional.empty());
            when(currentUserProvider.findAuditAuthor()).thenReturn(manager);

            tontineService.leaveTontine(TONTINE_ID);

            assertThat(tontine.getMembers()).hasSize(1);
            assertThat(remaining.getTurnOrder()).isEqualTo(1);
            verify(auditService).record(any(), eq(AuditAction.MEMBER_LEFT), eq("Tontine"), eq(TONTINE_ID), any());
        }

        @Test
        @DisplayName("refuse le depart d'un cycle engage et rappelle le tour a venir")
        void refusesLeavingActiveTontineBeforeTurn() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            tontine.setStatus(TontineStatus.ACTIVE);
            // Le tour du second participant tombe après le mois courant.
            tontine.setStartDate(YearMonth.now().atDay(1));

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(currentUserProvider.requireUserId()).thenReturn(101L);

            assertThatThrownBy(() -> tontineService.leaveTontine(TONTINE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("votre tour est prévu");

            assertThat(tontine.getMembers()).hasSize(2);
        }

        @Test
        @DisplayName("rappelle les cotisations dues a qui a déjà reçu la cagnotte")
        void refusesLeavingAfterReceivingPot() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            tontine.setStatus(TontineStatus.ACTIVE);
            tontine.setStartDate(YearMonth.now().atDay(1));

            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(currentUserProvider.requireUserId()).thenReturn(100L);

            assertThatThrownBy(() -> tontineService.leaveTontine(TONTINE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("déjà reçu la cagnotte");
        }

        @Test
        @DisplayName("refuse le depart d'un non participant")
        void refusesLeavingWithoutMembership() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(currentUserProvider.requireUserId()).thenReturn(999L);

            assertThatThrownBy(() -> tontineService.leaveTontine(TONTINE_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("annulation et suppression")
    class Closure {

        @Test
        @DisplayName("annule une tontine en cours sans effacer l'historique")
        void cancelsActiveTontine() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            tontine.setStatus(TontineStatus.ACTIVE);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(tontineRepository.save(any(Tontine.class))).thenAnswer(call -> call.getArgument(0));

            Tontine cancelled = tontineService.cancel(TONTINE_ID);

            assertThat(cancelled.getStatus()).isEqualTo(TontineStatus.CANCELLED);
            verify(tontineRepository, never()).delete(any(Tontine.class));
            verify(auditService).record(any(), eq(AuditAction.TONTINE_CANCELLED), eq("Tontine"), eq(TONTINE_ID), any());
        }

        @Test
        @DisplayName("supprime une tontine encore ouverte")
        void deletesDraftTontine() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(joinRequestRepository.findByTontineIdWithDetails(TONTINE_ID)).thenReturn(List.of());

            tontineService.delete(TONTINE_ID);

            verify(tontineRepository).delete(tontine);
            verify(auditService).record(any(), eq(AuditAction.TONTINE_DELETED), eq("Tontine"), eq(TONTINE_ID), any());
        }

        @Test
        @DisplayName("refuse de supprimer une tontine demarree, qui porte un historique")
        void refusesDeletingStartedTontine() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            tontine.setStatus(TontineStatus.ACTIVE);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));

            assertThatThrownBy(() -> tontineService.delete(TONTINE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("annulez-la");

            verify(tontineRepository, never()).delete(any(Tontine.class));
        }
    }

    @Nested
    @DisplayName("activation")
    class Activation {

        @Test
        @DisplayName("active une tontine conforme")
        void activatesValidTontine() {
            Tontine tontine = draftWithMembers(5, VALID_SALARY);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(tontineRepository.save(any(Tontine.class))).thenAnswer(call -> call.getArgument(0));
            when(currentUserProvider.requireUser()).thenReturn(manager);

            Tontine activated = tontineService.activate(TONTINE_ID);

            assertThat(activated.getStatus()).isEqualTo(TontineStatus.ACTIVE);
            verify(auditService).record(eq(manager), eq(AuditAction.TONTINE_ACTIVATED), eq("Tontine"), eq(TONTINE_ID), any());
        }

        @Test
        @DisplayName("refusé l'activation avec moins de deux participants")
        void rejectsActivationWithSingleMember() {
            Tontine tontine = draftWithMembers(1, VALID_SALARY);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));

            assertThatThrownBy(() -> tontineService.activate(TONTINE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("au moins 2 participants");

            assertThat(tontine.getStatus()).isEqualTo(TontineStatus.DRAFT);
        }

        @Test
        @DisplayName("refusé l'activation si un participant n'a pas de salaire de base")
        void rejectsActivationWithoutBaseSalary() {
            Tontine tontine = draftWithMembers(3, VALID_SALARY);
            TestEntities.addMember(tontine, TestEntities.employee(300L, "SansSalaire", BigDecimal.ZERO), 4, 400L);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));

            assertThatThrownBy(() -> tontineService.activate(TONTINE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("pas de salaire de base");
        }

        @Test
        @DisplayName("refusé l'activation si les cotisations d'un participant depassent son salaire")
        void rejectsActivationBeyondSalaryCapacity() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            // Ce participant s'est engage ailleurs entre-temps, au-dela de son salaire.
            when(tontineRepository.sumMonthlyCommitments(eq(100L), any()))
                    .thenReturn(new BigDecimal("600000"));

            assertThatThrownBy(() -> tontineService.activate(TONTINE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("dépasserait son salaire");

            assertThat(tontine.getStatus()).isEqualTo(TontineStatus.DRAFT);
        }

        @Test
        @DisplayName("fige la durée annoncee du cycle sur la composition réelle")
        void alignsTargetOnRealComposition() {
            Tontine tontine = draftWithMembers(3, VALID_SALARY);
            tontine.setTargetMemberCount(6);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
            when(tontineRepository.save(any(Tontine.class))).thenAnswer(call -> call.getArgument(0));
            when(currentUserProvider.requireUser()).thenReturn(manager);

            Tontine activated = tontineService.activate(TONTINE_ID);

            assertThat(activated.getTargetMemberCount()).isEqualTo(3);
            assertThat(activated.getProjectedEndMonth()).isEqualTo(START.plusMonths(2));
        }

        @Test
        @DisplayName("refuse une seconde activation")
        void rejectsSecondActivation() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            tontine.setStatus(TontineStatus.ACTIVE);
            when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));

            assertThatThrownBy(() -> tontineService.activate(TONTINE_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("DRAFT");
        }
    }

    @Nested
    @DisplayName("controle d'accès en lecture")
    class ReadAccess {

        @Test
        @DisplayName("autorise un gestionnaire sur n'importe quelle tontine")
        void allowsManager() {
            when(currentUserProvider.hasManagementPrivileges()).thenReturn(true);

            assertThatCode(() -> tontineService.checkReadAccess(draftWithMembers(2, VALID_SALARY)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("autorise un employé participant")
        void allowsParticipatingEmployee() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            tontine.setStatus(TontineStatus.ACTIVE);
            when(currentUserProvider.hasManagementPrivileges()).thenReturn(false);
            when(currentUserProvider.requireUserId()).thenReturn(100L);

            assertThatCode(() -> tontineService.checkReadAccess(tontine)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("autorise n'importe quel employé sur une tontine encore ouverte aux inscriptions")
        void allowsAnyEmployeeOnOpenTontine() {
            // Une tontine DRAFT est le catalogue des inscriptions : un employé
            // doit pouvoir l'examiner avant de demander a la rejoindre.
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            when(currentUserProvider.hasManagementPrivileges()).thenReturn(false);

            assertThatCode(() -> tontineService.checkReadAccess(tontine)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("refuse un employé non participant des que la tontine est activée")
        void rejectsNonParticipatingEmployeeOnceActive() {
            Tontine tontine = draftWithMembers(2, VALID_SALARY);
            tontine.setStatus(TontineStatus.ACTIVE);
            when(currentUserProvider.hasManagementPrivileges()).thenReturn(false);
            when(currentUserProvider.requireUserId()).thenReturn(999L);

            assertThatThrownBy(() -> tontineService.checkReadAccess(tontine))
                    .isInstanceOf(UnauthorizedOperationException.class)
                    .hasMessageContaining("pas participant");
        }
    }

    @Test
    @DisplayName("modifie une tontine encore au statut DRAFT")
    void updatesDraftTontine() {
        Tontine tontine = draftWithMembers(2, VALID_SALARY);
        when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));
        when(tontineRepository.save(any(Tontine.class))).thenAnswer(call -> call.getArgument(0));
        when(currentUserProvider.requireUser()).thenReturn(manager);

        Tontine updated = tontineService.update(TONTINE_ID,
                new UpdateTontineRequest("Nouveau nom", new BigDecimal("75000"), null, null, null, null));

        assertThat(updated.getName()).isEqualTo("Nouveau nom");
        assertThat(updated.getMonthlyAmount()).isEqualByComparingTo("75000");
    }

    @Test
    @DisplayName("refuse la modification d'une tontine ACTIVE")
    void rejectsUpdateOnActiveTontine() {
        Tontine tontine = draftWithMembers(2, VALID_SALARY);
        tontine.setStatus(TontineStatus.ACTIVE);
        when(tontineRepository.findByIdWithMembers(TONTINE_ID)).thenReturn(Optional.of(tontine));

        assertThatThrownBy(() -> tontineService.update(TONTINE_ID,
                new UpdateTontineRequest(null, new BigDecimal("99999"), null, null, null, null)))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(tontine.getMonthlyAmount()).isEqualByComparingTo(MONTHLY_AMOUNT);
    }
}
