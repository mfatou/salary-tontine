package com.salarytontine.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("SalaryCalculator")
class SalaryCalculatorTest {

    private final SalaryCalculator calculator = new SalaryCalculator();

    @Test
    @DisplayName("cas normal : 500000 - 50000 = 450000")
    void calculatesNonBeneficiarySalary() {
        var result = calculator.calculate(
                new BigDecimal("500000"), new BigDecimal("50000"), BigDecimal.ZERO);

        assertThat(result.finalSalary()).isEqualByComparingTo("450000");
        assertThat(result.tontineReceived()).isEqualByComparingTo("0");
        assertThat(result.tontineDeduction()).isEqualByComparingTo("50000");
    }

    @Test
    @DisplayName("cas bénéficiaire : 500000 - 50000 + 250000 = 700000")
    void calculatesBeneficiarySalary() {
        BigDecimal pot = calculator.calculatePot(new BigDecimal("50000"), 5);
        var result = calculator.calculate(new BigDecimal("500000"), new BigDecimal("50000"), pot);

        assertThat(pot).isEqualByComparingTo("250000");
        assertThat(result.finalSalary()).isEqualByComparingTo("700000");
    }

    @ParameterizedTest(name = "{0} x {1} participants = {2}")
    @CsvSource({
            "50000,  5, 250000",
            "50000,  2, 100000",
            "25000,  4, 100000",
            "10000, 12, 120000",
            "12500,  3,  37500"
    })
    @DisplayName("calcule la cagnotte comme montant mensuel x participants")
    void calculatesPot(String monthlyAmount, int participants, String expectedPot) {
        assertThat(calculator.calculatePot(new BigDecimal(monthlyAmount), participants))
                .isEqualByComparingTo(expectedPot);
    }

    @ParameterizedTest(name = "base={0} deduction={1} reçu={2} attendu={3}")
    @CsvSource({
            "500000, 50000,      0, 450000",
            "500000, 50000, 250000, 700000",
            "450000, 50000,      0, 400000",
            "600000, 50000, 250000, 800000",
            "400000, 50000,      0, 350000"
    })
    @DisplayName("applique finalSalary = base - deduction + reçu")
    void appliesFormula(String base, String deduction, String received, String expected) {
        var result = calculator.calculate(
                new BigDecimal(base), new BigDecimal(deduction), new BigDecimal(received));

        assertThat(result.finalSalary()).isEqualByComparingTo(expected);
    }

    @Test
    @DisplayName("preserve la precision decimale des montants")
    void preservesDecimalPrecision() {
        var result = calculator.calculate(
                new BigDecimal("500000.55"), new BigDecimal("50000.15"), new BigDecimal("250000.10"));

        assertThat(result.finalSalary()).isEqualByComparingTo("700000.50");
    }

    @Test
    @DisplayName("refuse un salaire de base negatif")
    void rejectsNegativeBaseSalary() {
        assertThatThrownBy(() -> calculator.calculate(
                new BigDecimal("-1"), BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("salaire de base");
    }

    @Test
    @DisplayName("refuse une cotisation negative")
    void rejectsNegativeDeduction() {
        assertThatThrownBy(() -> calculator.calculate(
                new BigDecimal("500000"), new BigDecimal("-50000"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cotisation");
    }

    @Test
    @DisplayName("refuse un montant null")
    void rejectsNullAmount() {
        assertThatThrownBy(() -> calculator.calculate(null, BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("obligatoire");
    }

    @Test
    @DisplayName("la somme des salaires simules egale la somme des salaires de base")
    void conservesTotalPayroll() {
        BigDecimal monthlyAmount = new BigDecimal("50000");
        BigDecimal baseSalary = new BigDecimal("500000");
        int participants = 5;
        BigDecimal pot = calculator.calculatePot(monthlyAmount, participants);

        BigDecimal totalSimulated = BigDecimal.ZERO;
        for (int index = 0; index < participants; index++) {
            BigDecimal received = (index == 0) ? pot : BigDecimal.ZERO;
            totalSimulated = totalSimulated.add(
                    calculator.calculate(baseSalary, monthlyAmount, received).finalSalary());
        }

        BigDecimal totalBase = baseSalary.multiply(BigDecimal.valueOf(participants));
        assertThat(totalSimulated).isEqualByComparingTo(totalBase);
    }
}
