package com.salarytontine.dto.response;

import com.salarytontine.enums.ContributionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record ContributionResponse(
        Long id,
        Long tontineId,
        String tontineName,
        Long userId,
        String userName,
        BigDecimal amount,
        YearMonth contributionMonth,
        Integer periodIndex,
        LocalDate periodStart,
        ContributionStatus status) {
}
