package com.salarytontine.dto.response;

import com.salarytontine.enums.JoinRequestStatus;
import java.time.Instant;

public record JoinRequestResponse(
        Long id,
        Long tontineId,
        String tontineName,
        Long userId,
        String userName,
        String userEmail,
        JoinRequestStatus status,
        String motivation,
        String decisionNote,
        Instant requestedAt,
        Instant decidedAt,
        String decidedByName) {
}
