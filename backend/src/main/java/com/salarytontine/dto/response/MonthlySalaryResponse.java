package com.salarytontine.dto.response;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/**
 * Bulletin simule d'un mois.
 *
 * <p>Un employé pouvant cotiser a plusieurs tontines, le salaire final n'a de
 * sens qu'au niveau du mois : {@code lines} detaille l'effet de chaque tontine,
 * les totaux portent le resultat consolide.</p>
 */
public record MonthlySalaryResponse(
        YearMonth month,
        BigDecimal baseSalary,
        BigDecimal totalDeduction,
        BigDecimal totalReceived,
        BigDecimal finalSalary,
        List<SalaryRecordResponse> lines) {
}
