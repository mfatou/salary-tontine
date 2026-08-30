package com.salarytontine.dto.response;

import java.time.LocalDate;

/**
 * Ligne du calendrier prévisionnel : quel participant reçoit la cagnotte, à
 * quel tour et sur quelles dates.
 */
public record ScheduleEntryResponse(
        Integer periodIndex,
        LocalDate start,
        LocalDate end,
        Long beneficiaryUserId,
        String beneficiaryName,
        Integer turnOrder) {
}
