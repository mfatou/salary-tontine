package com.salarytontine.dto.response;

import java.util.List;

public record TontineDetailResponse(
        TontineResponse tontine,
        List<TontineMemberResponse> members) {
}
