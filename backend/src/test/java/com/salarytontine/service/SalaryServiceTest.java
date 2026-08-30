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
import com.salarytontine.entity.SalaryRecord;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.User;
import com.salarytontine.enums.AuditAction;
import com.salarytontine.enums.ContributionStatus;
import com.salarytontine.enums.TontineStatus;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.exception.DuplicateResourceException;
import com.salarytontine.repository.ContributionRepository;
import com.salarytontine.repository.SalaryRecordRepository;
import com.salarytontine.repository.TontineRepository;
import com.salarytontine.security.CurrentUserProvider;
import com.salarytontine.support.TestEntities;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("SalaryService")
class SalaryServiceTest {

    private static final Long TONTINE_ID = 10L;
    private static final BigDecimal MONTHLY_AMOUNT = new BigDecimal("50000");
    private static final BigDecimal BASE_SALARY = new BigDecimal("500000");
    /** La tontine de test démarre en août 2026 : le tour 1 est août, le tour 5 décembre. */
    private static final YearMonth FIRST_MONTH = YearMonth.of(2026, 8);
    private static final YearMonth SECOND_MONTH = YearMonth.of(2026, 9);
    private static final YearMonth LAST_MONTH = YearMonth.of(2026, 12);
    private static final List<String> MEMBER_NAMES =
            List.of("Awa", "Fatou", "Mamadou", "Khady", "Aliou");

    @Mock
    private SalaryRecordRepository salaryRecordRepository;

    @Mock
    private ContributionRepository contributionRepository;

    @Mock
    private TontineRepository tontineRepository;

    @Mock
    private TontineService tontineService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private AuditService auditService;

    private SalaryService salaryService;
    private User manager;

    @BeforeEach
    void setUp() {
        salaryService = new SalaryService(salaryRecordRepository, contributionRepository, tontineRepository,
                tontineService, new TontineCycleService(), new SalaryCalculator(),
                currentUserProvider, auditService);
        manager = TestEntities.manager(1L);
    }

    private Tontine activeTontine() {
        Tontine tontine = TestEntities.activeTontine(TONTINE_ID, MONTHLY_AMOUNT, FIRST_MONTH, manager);
        for (int index = 0; index < MEMBER_NAMES.size(); index++) {
            TestEntities.addMember(tontine,
                    TestEntities.employee(100L + index, MEMBER_NAMES.get(index), BASE_SALARY),
                    index + 1, 200L + index);
        }
        return tontine;
    }

    private List<Contribution> contributionsFor(Tontine tontine, int periodIndex) {
        List<Contribution> contributions = new ArrayList<>();
        long id = 1;
        for (var member : tontine.getMembers()) {
            contributions.add(TestEntities.contribution(id++, tontine, member.getUser(), periodIndex));
        }
        return contributions;
    }

    private void stubSuccessfulGeneration(Tontine tontine, int periodIndex) {
        when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
        when(salaryRecordRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, periodIndex)).thenReturn(false);
        when(contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(TONTINE_ID, periodIndex))
                .thenReturn(contributionsFor(tontine, periodIndex));
        when(salaryRecordRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(currentUserProvider.findAuditAuthor()).thenReturn(manager);
    }

    private SalaryRecord recordOf(List<SalaryRecord> records, String name) {
        return records.stream()
                .filter(record -> record.getUser().getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Aucun salaire pour " + name));
    }

    @Nested
    @DisplayName("calcul mensuel")
    class MonthlyCalculation {

        @Test
        @DisplayName("crée un salaire simule par participant")
        void createsOneRecordPerMember() {
            stubSuccessfulGeneration(activeTontine(), 1);

            List<SalaryRecord> records = salaryService.generateForPeriod(TONTINE_ID, 1);

            assertThat(records).hasSize(MEMBER_NAMES.size());
            // Le tour 1 d'une tontine mensuelle démarrée en août tombe en août.
            assertThat(records).allMatch(record -> record.getSalaryMonth().equals(FIRST_MONTH));
            assertThat(records).allMatch(record -> record.getPeriodIndex() == 1);
        }

        @Test
        @DisplayName("Awa est bénéficiaire en août : 500000 - 50000 + 250000 = 700000")
        void beneficiaryReceivesPotInAugust() {
            stubSuccessfulGeneration(activeTontine(), 1);

            List<SalaryRecord> records = salaryService.generateForPeriod(TONTINE_ID, 1);
            SalaryRecord awa = recordOf(records, "Awa");

            assertThat(awa.getBaseSalary()).isEqualByComparingTo("500000");
            assertThat(awa.getTontineDeduction()).isEqualByComparingTo("50000");
            assertThat(awa.getTontineReceived()).isEqualByComparingTo("250000");
            assertThat(awa.getFinalSalary()).isEqualByComparingTo("700000");
            assertThat(awa.isBeneficiary()).isTrue();
        }

        @Test
        @DisplayName("les non-bénéficiaires d'août tombent a 450000")
        void nonBeneficiariesAreDeductedOnly() {
            stubSuccessfulGeneration(activeTontine(), 1);

            List<SalaryRecord> records = salaryService.generateForPeriod(TONTINE_ID, 1);

            for (String name : List.of("Fatou", "Mamadou", "Khady", "Aliou")) {
                SalaryRecord record = recordOf(records, name);
                assertThat(record.getTontineReceived()).isEqualByComparingTo("0");
                assertThat(record.getFinalSalary()).isEqualByComparingTo("450000");
                assertThat(record.isBeneficiary()).isFalse();
            }
        }

        @Test
        @DisplayName("Fatou devient bénéficiaire en septembre")
        void beneficiaryChangesNextMonth() {
            stubSuccessfulGeneration(activeTontine(), 2);

            List<SalaryRecord> records = salaryService.generateForPeriod(TONTINE_ID, 2);

            assertThat(recordOf(records, "Fatou").getFinalSalary()).isEqualByComparingTo("700000");
            assertThat(recordOf(records, "Awa").getFinalSalary()).isEqualByComparingTo("450000");
        }

        @Test
        @DisplayName("verse la cagnotte a un seul participant par mois")
        void exactlyOneBeneficiaryPerMonth() {
            stubSuccessfulGeneration(activeTontine(), 1);

            List<SalaryRecord> records = salaryService.generateForPeriod(TONTINE_ID, 1);

            assertThat(records.stream().filter(SalaryRecord::isBeneficiary)).hasSize(1);
        }

        @Test
        @DisplayName("marque les cotisations du mois comme DEDUCTED")
        void marksContributionsAsDeducted() {
            Tontine tontine = activeTontine();
            List<Contribution> contributions = contributionsFor(tontine, 1);

            when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
            when(salaryRecordRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, 1)).thenReturn(false);
            when(contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(TONTINE_ID, 1))
                    .thenReturn(contributions);
            when(salaryRecordRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
            when(currentUserProvider.findAuditAuthor()).thenReturn(manager);

            salaryService.generateForPeriod(TONTINE_ID, 1);

            assertThat(contributions).allMatch(c -> c.getStatus() == ContributionStatus.DEDUCTED);
            verify(contributionRepository).saveAll(contributions);
        }
    }

    @Nested
    @DisplayName("garde-fous")
    class Guards {

        @Test
        @DisplayName("refuse une seconde génération pour le même mois")
        void rejectsDuplicateGeneration() {
            when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(activeTontine());
            when(salaryRecordRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, 1)).thenReturn(true);

            assertThatThrownBy(() -> salaryService.generateForPeriod(TONTINE_ID, 1))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("déjà été générés");

            verify(salaryRecordRepository, never()).saveAll(anyList());
        }

        @Test
        @DisplayName("refuse un tour antérieur au début du cycle")
        void rejectsMonthBeforeCycle() {
            when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(activeTontine());

            assertThatThrownBy(() -> salaryService.generateForPeriod(TONTINE_ID, 0))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("précède le début");
        }

        @Test
        @DisplayName("refuse un tour postérieur à la fin du cycle")
        void rejectsMonthAfterCycle() {
            when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(activeTontine());

            assertThatThrownBy(() -> salaryService.generateForPeriod(TONTINE_ID, 6))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("dépasse la fin");
        }

        @Test
        @DisplayName("refuse la génération si les cotisations du mois n'existent pas")
        void rejectsMissingContributions() {
            when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(activeTontine());
            when(salaryRecordRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, 1)).thenReturn(false);
            when(contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(TONTINE_ID, 1))
                    .thenReturn(List.of());

            assertThatThrownBy(() -> salaryService.generateForPeriod(TONTINE_ID, 1))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("cotisations du tour 1 doivent être générées");
        }

        @Test
        @DisplayName("refuse la génération si les cotisations sont incomplètes")
        void rejectsIncompleteContributions() {
            Tontine tontine = activeTontine();
            when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(tontine);
            when(salaryRecordRepository.existsByTontineIdAndPeriodIndex(TONTINE_ID, 1)).thenReturn(false);
            when(contributionRepository.findByTontineIdAndPeriodIndexOrderByIdAsc(TONTINE_ID, 1))
                    .thenReturn(contributionsFor(tontine, 1).subList(0, 3));

            assertThatThrownBy(() -> salaryService.generateForPeriod(TONTINE_ID, 1))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cotisations incomplètes");
        }

        @Test
        @DisplayName("refuse la génération sur une tontine non ACTIVE")
        void rejectsNonActiveTontine() {
            Tontine draft = activeTontine();
            draft.setStatus(TontineStatus.DRAFT);
            when(tontineService.findByIdWithMembers(TONTINE_ID)).thenReturn(draft);

            assertThatThrownBy(() -> salaryService.generateForPeriod(TONTINE_ID, 1))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("ACTIVE");
        }
    }

    @Nested
    @DisplayName("fin de cycle")
    class CycleCompletion {

        @Test
        @DisplayName("passe la tontine a COMPLETED après le dernier mois")
        void completesTontineAfterLastMonth() {
            Tontine tontine = activeTontine();
            stubSuccessfulGeneration(tontine, 5);
            when(tontineRepository.save(tontine)).thenReturn(tontine);

            salaryService.generateForPeriod(TONTINE_ID, 5);

            assertThat(tontine.getStatus()).isEqualTo(TontineStatus.COMPLETED);
            verify(auditService).record(eq(manager), eq(AuditAction.TONTINE_COMPLETED),
                    eq("Tontine"), eq(TONTINE_ID), any());
        }

        @Test
        @DisplayName("laisse la tontine ACTIVE tant que le cycle n'est pas terminé")
        void keepsTontineActiveBeforeLastMonth() {
            Tontine tontine = activeTontine();
            stubSuccessfulGeneration(tontine, 2);

            salaryService.generateForPeriod(TONTINE_ID, 2);

            assertThat(tontine.getStatus()).isEqualTo(TontineStatus.ACTIVE);
            verify(tontineRepository, never()).save(any(Tontine.class));
        }
    }

    @Test
    @DisplayName("trace la génération des salaires dans le journal d'audit")
    void recordsAuditLog() {
        stubSuccessfulGeneration(activeTontine(), 1);

        salaryService.generateForPeriod(TONTINE_ID, 1);

        verify(auditService).record(eq(manager), eq(AuditAction.SALARIES_GENERATED),
                eq("Tontine"), eq(TONTINE_ID), any());
    }
}
