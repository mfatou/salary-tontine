package com.salarytontine.dto.response;

public record TontineMemberResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        Integer turnOrder) {
}
