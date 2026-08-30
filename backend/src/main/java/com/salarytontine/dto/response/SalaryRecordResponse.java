package com.salarytontine.dto.response;

import java.math.BigDecimal;
import java.time.YearMonth;

public record SalaryRecordResponse(
        Long id,
        Long userId,
        String userName,
        Long tontineId,
        String tontineName,
        YearMonth salaryMonth,
        BigDecimal baseSalary,
        BigDecimal tontineDeduction,
        BigDecimal tontineReceived,
        BigDecimal finalSalary,
        boolean beneficiary) {
}
