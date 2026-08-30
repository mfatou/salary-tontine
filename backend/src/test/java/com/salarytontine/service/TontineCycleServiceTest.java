package com.salarytontine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.User;
import com.salarytontine.enums.TontineFrequency;
import com.salarytontine.exception.BusinessRuleException;
import com.salarytontine.support.TestEntities;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TontineCycleService")
class TontineCycleServiceTest {

    private static final Long TONTINE_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("50000");
    private static final BigDecimal SALARY = new BigDecimal("500000");
    private static final LocalDate START = LocalDate.of(2026, 8, 1);

    private final TontineCycleService cycleService = new TontineCycleService();

    private Tontine tontineWith(TontineFrequency frequency, int memberCount) {
        User manager = TestEntities.manager(1L);
        Tontine tontine = TestEntities.tontine(TONTINE_ID, AMOUNT, START, manager, frequency);
        for (int index = 0; index < memberCount; index++) {
            TestEntities.addMember(tontine,
                    TestEntities.employee(100L + index, "Employe" + index, SALARY),
                    index + 1, 200L + index);
        }
        return tontine;
    }

    @Nested
    @DisplayName("découpage selon la cadence")
    class PeriodBoundaries {

        @ParameterizedTest(name = "{0} : le tour {1} commence le {2} et finit le {3}")
        @CsvSource({
                "MONTHLY,   1, 2026-08-01, 2026-08-31",
                "MONTHLY,   2, 2026-09-01, 2026-09-30",
                "MONTHLY,   5, 2026-12-01, 2026-12-31",
                "WEEKLY,    1, 2026-08-01, 2026-08-07",
                "WEEKLY,    2, 2026-08-08, 2026-08-14",
                "WEEKLY,    5, 2026-08-29, 2026-09-04",
                "TEN_DAYS,  1, 2026-08-01, 2026-08-10",
                "TEN_DAYS,  3, 2026-08-21, 2026-08-30",
                "BIWEEKLY,  1, 2026-08-01, 2026-08-14",
                "BIWEEKLY,  2, 2026-08-15, 2026-08-28",
        })
        @DisplayName("borne chaque tour")
        void computesPeriodBounds(TontineFrequency frequency, int periodIndex,
                                  LocalDate expectedStart, LocalDate expectedEnd) {
            Tontine tontine = tontineWith(frequency, 5);

            assertThat(tontine.periodStart(periodIndex)).isEqualTo(expectedStart);
            assertThat(tontine.periodEnd(periodIndex)).isEqualTo(expectedEnd);
        }

        @Test
        @DisplayName("les tours se suivent sans trou ni recouvrement")
        void periodsAreContiguous() {
            Tontine tontine = tontineWith(TontineFrequency.TEN_DAYS, 5);

            for (int index = 1; index < 5; index++) {
                assertThat(tontine.periodEnd(index).plusDays(1))
                        .isEqualTo(tontine.periodStart(index + 1));
            }
        }

        @Test
        @DisplayName("le cycle dure un tour par participant")
        void cycleLengthFollowsMembership() {
            assertThat(cycleService.cycleLength(tontineWith(TontineFrequency.WEEKLY, 3))).isEqualTo(3);
            // Trois semaines à partir du 1er août : fin le 21.
            assertThat(cycleService.lastPeriodEnd(tontineWith(TontineFrequency.WEEKLY, 3)))
                    .isEqualTo(LocalDate.of(2026, 8, 21));
        }
    }

    @Nested
    @DisplayName("bénéficiaire et bornes du cycle")
    class BeneficiaryResolution {

        @Test
        @DisplayName("attribue chaque tour au participant de même rang")
        void resolvesBeneficiaryByTurnOrder() {
            Tontine tontine = tontineWith(TontineFrequency.MONTHLY, 5);

            for (int index = 1; index <= 5; index++) {
                assertThat(cycleService.resolveBeneficiary(tontine, index).getTurnOrder())
                        .isEqualTo(index);
            }
        }

        @Test
        @DisplayName("chaque participant reçoit exactement une fois")
        void everyMemberIsServedOnce() {
            Tontine tontine = tontineWith(TontineFrequency.WEEKLY, 5);

            List<Long> beneficiaires = cycleService.buildSchedule(tontine).stream()
                    .map(slot -> slot.beneficiary().getUser().getId())
                    .toList();

            assertThat(beneficiaires).hasSize(5).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("refuse un tour hors du cycle")
        void rejectsOutOfCycle() {
            Tontine tontine = tontineWith(TontineFrequency.MONTHLY, 5);

            assertThatThrownBy(() -> cycleService.requireWithinCycle(tontine, 0))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("précède le début");

            assertThatThrownBy(() -> cycleService.requireWithinCycle(tontine, 6))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("dépasse la fin");
        }

        @Test
        @DisplayName("situe une date dans le bon tour")
        void resolvesPeriodFromDate() {
            Tontine tontine = tontineWith(TontineFrequency.TEN_DAYS, 5);

            assertThat(cycleService.resolvePeriodIndex(tontine, LocalDate.of(2026, 8, 1))).isEqualTo(1);
            assertThat(cycleService.resolvePeriodIndex(tontine, LocalDate.of(2026, 8, 10))).isEqualTo(1);
            assertThat(cycleService.resolvePeriodIndex(tontine, LocalDate.of(2026, 8, 11))).isEqualTo(2);
            assertThat(cycleService.resolvePeriodIndex(tontine, LocalDate.of(2026, 9, 19))).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("tours échus")
    class DuePeriods {

        @Test
        @DisplayName("ne retourne rien avant le début du cycle")
        void nothingBeforeStart() {
            Tontine tontine = tontineWith(TontineFrequency.WEEKLY, 4);

            assertThat(cycleService.duePeriods(tontine, LocalDate.of(2026, 7, 31))).isEmpty();
        }

        @Test
        @DisplayName("rattrape tous les tours écoulés, pas seulement le dernier")
        void catchesUpEveryElapsedPeriod() {
            Tontine tontine = tontineWith(TontineFrequency.WEEKLY, 4);

            // Trois semaines après le départ : les tours 1 à 3 sont échus.
            assertThat(cycleService.duePeriods(tontine, LocalDate.of(2026, 8, 20)))
                    .containsExactly(1, 2, 3);
        }

        @Test
        @DisplayName("ne dépasse jamais la fin du cycle")
        void staysWithinCycle() {
            Tontine tontine = tontineWith(TontineFrequency.WEEKLY, 4);

            assertThat(cycleService.duePeriods(tontine, LocalDate.of(2027, 5, 1)))
                    .containsExactly(1, 2, 3, 4);
        }
    }

    @Nested
    @DisplayName("cadence libre")
    class CustomFrequency {

        private Tontine tousLesDeuxJours(int memberCount) {
            User manager = TestEntities.manager(1L);
            Tontine tontine = TestEntities.tontine(TONTINE_ID, AMOUNT, START, manager,
                    TontineFrequency.CUSTOM, 2);
            for (int index = 0; index < memberCount; index++) {
                TestEntities.addMember(tontine,
                        TestEntities.employee(100L + index, "Employe" + index, SALARY),
                        index + 1, 200L + index);
            }
            return tontine;
        }

        @Test
        @DisplayName("découpe le cycle selon la durée choisie")
        void slicesByChosenLength() {
            Tontine tontine = tousLesDeuxJours(4);

            assertThat(tontine.periodLengthInDays()).isEqualTo(2);
            assertThat(tontine.periodStart(1)).isEqualTo(LocalDate.of(2026, 8, 1));
            assertThat(tontine.periodEnd(1)).isEqualTo(LocalDate.of(2026, 8, 2));
            assertThat(tontine.periodStart(4)).isEqualTo(LocalDate.of(2026, 8, 7));
            // Quatre tours de deux jours : le cycle tient en huit jours.
            assertThat(cycleService.lastPeriodEnd(tontine)).isEqualTo(LocalDate.of(2026, 8, 8));
        }

        @Test
        @DisplayName("situe une date dans le bon tour")
        void resolvesPeriodFromDate() {
            Tontine tontine = tousLesDeuxJours(4);

            assertThat(cycleService.resolvePeriodIndex(tontine, LocalDate.of(2026, 8, 2))).isEqualTo(1);
            assertThat(cycleService.resolvePeriodIndex(tontine, LocalDate.of(2026, 8, 3))).isEqualTo(2);
            assertThat(cycleService.resolvePeriodIndex(tontine, LocalDate.of(2026, 8, 8))).isEqualTo(4);
        }

        @Test
        @DisplayName("pèse très lourd une fois ramenée au mois")
        void weighsHeavilyPerMonth() {
            // 50 000 tous les deux jours, soit plus de quinze prélèvements
            // par mois : la règle de capacité doit voir ce poids réel.
            assertThat(tousLesDeuxJours(4).monthlyCost()).isEqualByComparingTo("760937.50");
        }

        @Test
        @DisplayName("une cadence prédéfinie ignore la durée fournie")
        void presetIgnoresExplicitLength() {
            User manager = TestEntities.manager(1L);
            Tontine tontine = TestEntities.tontine(TONTINE_ID, AMOUNT, START, manager,
                    TontineFrequency.WEEKLY, 2);

            // Deux sources de vérité pour la même durée finiraient par diverger :
            // seule celle de l'énumération fait foi hors cadence libre.
            assertThat(tontine.getPeriodDays()).isNull();
            assertThat(tontine.periodLengthInDays()).isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("coût mensuel selon la cadence")
    class MonthlyCost {

        @Test
        @DisplayName("ramène une cotisation infra-mensuelle à son poids réel")
        void convertsToMonthlyCost() {
            // Une cotisation hebdomadaire de 50 000 pèse bien plus qu'une
            // mensuelle du même montant : sans cette conversion, le plafond de
            // cotisation laisserait passer des engagements intenables.
            assertThat(tontineWith(TontineFrequency.MONTHLY, 3).monthlyCost())
                    .isEqualByComparingTo("50000.00");
            assertThat(tontineWith(TontineFrequency.WEEKLY, 3).monthlyCost())
                    .isEqualByComparingTo("217410.71");
            assertThat(tontineWith(TontineFrequency.TEN_DAYS, 3).monthlyCost())
                    .isEqualByComparingTo("152187.50");
        }
    }
}
