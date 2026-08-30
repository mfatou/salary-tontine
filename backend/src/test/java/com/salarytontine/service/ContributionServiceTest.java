package com.salarytontine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.salarytontine.entity.Contribution;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.ContributionStatus;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.repository.ContributionRepository;
import com.salarytontine.security.CurrentUserProvider;
import com.salarytontine.support.TestEntities;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContributionService")
class ContributionServiceTest {

    private static final Long TONTINE_ID = 10L;
    private static final BigDecimal MONTHLY_AMOUNT = new BigDecimal("50000");
    private static final YearMonth START = YearMonth.of(2026, 8);
    /** La tontine démarre en août : le tour 1 couvre ce mois. */
    private static final int FIRST_PERIOD = 1;
    private static final int MEMBER_COUNT = 5;

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private TontineService tontineService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditService auditService;

    private ContributionService contributionService;
    private User manager;

    @BeforeEach
    void setUp() {
        contributionService = new ContributionService(contributionRepository, tontineService,
                new TontineCycleService(), currentUserProvider, auditService);
        manager = TestEntities.manager(1L);
    }

    private Tontine activeTontine() {
        Tontine tontine = TestEntities.activeTontine(TONTINE_ID, MONTHLY_AMOUNT, START, manager);
        for (int index = 0; index < MEMBER_COUNT; index++) {
            TestEntities.addMember(tontine,
                    TestEntities.employee(100L + index, "Employé" + index, new BigDecimal("500000")),
                    index + 1, 200L + index);
        }
        return tontine;
    }

    @Test
    @DisplayName("crée une cotisation par participant")
    void createsOneContributionPerMember() {
        Tontine tontine = activeTontine();
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
        when(contributionRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, FIRST_PERIOD)).thenReturn(false);
        when(contributionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(currentUserProvider.findAuditAuthor()).thenReturn(manager);

        List<Contribution> contributions = contributionService.generateForPeriod(TONTINE_ID, FIRST_PERIOD);

        assertThat(contributions).hasSize(MEMBER_COUNT);
        assertThat(contributions).allSatisfy(contribution -> {
            assertThat(contribution.getContributionMonth()).isEqualTo(START);
            assertThat(contribution.getStatus()).isEqualTo(ContributionStatus.PENDING);
        });
    }

    @Test
    @DisplayName("utilise le montant enregistre dans la tontine et non une valeur cliente")
    void usesTontineMonthlyAmount() {
        Tontine tontine = activeTontine();
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
        when(contributionRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, FIRST_PERIOD)).thenReturn(false);
        when(contributionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(currentUserProvider.findAuditAuthor()).thenReturn(manager);

        List<Contribution> contributions = contributionService.generateForPeriod(TONTINE_ID, FIRST_PERIOD);

        assertThat(contributions).allMatch(
                contribution -> contribution.getAmount().compareTo(MONTHLY_AMOUNT) == 0);
    }

    @Test
    @DisplayName("refuse une seconde génération pour le même mois")
    void rejectsDuplicateGeneration() {
        Tontine tontine = activeTontine();
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
        when(contributionRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, FIRST_PERIOD)).thenReturn(true);

        assertThatThrownBy(() -> contributionService.generateForPeriod(TONTINE_ID, FIRST_PERIOD))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("déjà été générées");

        verify(contributionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("refuse la génération sur une tontine non ACTIVE")
    void rejectsGenerationOnDraftTontine() {
        Tontine draft = activeTontine();
        draft.setStatus(TontineStatus.DRAFT);
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(draft);

        assertThatThrownBy(() -> contributionService.generateForPeriod(TONTINE_ID, FIRST_PERIOD))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("ACTIVE");

        verify(contributionRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("refuse un tour antérieur au début du cycle")
    void rejectsMonthBeforeCycle() {
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(activeTontine());

        assertThatThrownBy(() -> contributionService.generateForPeriod(TONTINE_ID, 0))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("précède le début");
    }

    @Test
    @DisplayName("refuse un tour postérieur à la fin du cycle")
    void rejectsMonthAfterCycle() {
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(activeTontine());

        assertThatThrownBy(() -> contributionService.generateForPeriod(TONTINE_ID, 6))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("dépasse la fin");
    }

    @Test
    @DisplayName("trace la génération dans le journal d'audit")
    void recordsAuditLog() {
        Tontine tontine = activeTontine();
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
        when(contributionRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, FIRST_PERIOD)).thenReturn(false);
        when(contributionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(currentUserProvider.findAuditAuthor()).thenReturn(manager);

        contributionService.generateForPeriod(TONTINE_ID, FIRST_PERIOD);

        verify(auditService).record(eq(manager), eq(AuditAction.CONTRIBUTIONS_GENERATED),
                eq("Tontine"), eq(TONTINE_ID), any());
    }

    @Test
    @DisplayName("limite un employé a ses propres cotisations")
    void filtersContributionsForEmployee() {
        Tontine tontine = activeTontine();
        User first = tontine.getMembers().get(0).getUser();
        User second = tontine.getMembers().get(1).getUser();
        List<Contribution> all = List.of(
                TestEntities.contribution(1L, tontine, first, FIRST_PERIOD),
                TestEntities.contribution(2L, tontine, second, FIRST_PERIOD));

        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
        when(contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(TONTINE_ID, FIRST_PERIOD))
                .thenReturn(all);
        when(currentUserProvider.hasManagementPrivileges()).thenReturn(false);
        when(currentUserProvider.requireUserId()).thenReturn(first.getId());

        List<Contribution> visible = contributionService.findByTontine(TONTINE_ID, FIRST_PERIOD);

        assertThat(visible).hasSize(1);
        assertThat(visible.get(0).getUser()).isEqualTo(first);
    }

    @Test
    @DisplayName("laisse un gestionnaire consulter toutes les cotisations")
    void returnsAllContributionsForManager() {
        Tontine tontine = activeTontine();
        List<Contribution> all = List.of(
                TestEntities.contribution(1L, tontine, tontine.getMembers().get(0).getUser(), FIRST_PERIOD),
                TestEntities.contribution(2L, tontine, tontine.getMembers().get(1).getUser(), FIRST_PERIOD));

        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
        when(contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(TONTINE_ID, FIRST_PERIOD))
                .thenReturn(all);
        when(currentUserProvider.hasManagementPrivileges()).thenReturn(true);

        assertThat(contributionService.findByTontine(TONTINE_ID, FIRST_PERIOD)).hasSize(2);
    }
}
