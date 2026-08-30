package com.salarytontine.mapper;

import com.salarytontine.dto.response.TontineDetailResponse;
import com.salarytontine.dto.response.TontineMemberResponse;
import com.salarytontine.dto.response.TontineResponse;
import com.salarytontine.entity.Tontine;
import com.salarytontine.entity.TontineMember;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TontineMapper {

    public TontineResponse toResponse(Tontine tontine) {
        return new TontineResponse(
                tontine.getId(),
                tontine.getName(),
                tontine.getMonthlyAmount(),
                tontine.getStartDate(),
                tontine.getStartMonth(),
                tontine.getStatus(),
                tontine.getFrequency(),
                tontine.periodLengthInDays(),
                tontine.getMembers().size(),
                tontine.calculatePotAmount(),
                tontine.getTargetMemberCount(),
                tontine.getRemainingSeats(),
                tontine.getProjectedEndMonth(),
                tontine.getProjectedEndDate(),
                tontine.monthlyCost(),
                tontine.getCreatedBy().getName(),
                tontine.getCreatedAt());
    }

    public List<TontineResponse> toResponses(List<Tontine> tontines) {
        return tontines.stream().map(this::toResponse).toList();
    }

    public TontineMemberResponse toMemberResponse(TontineMember member) {
        return new TontineMemberResponse(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getEmail(),
                member.getTurnOrder());
    }

    public List<TontineMemberResponse> toMemberResponses(List<TontineMember> members) {
        return members.stream()
                .sorted(Comparator.comparing(TontineMember::getTurnOrder))
                .map(this::toMemberResponse)
                .toList();
    }

    public TontineDetailResponse toDetailResponse(Tontine tontine) {
        return new TontineDetailResponse(toResponse(tontine), toMemberResponses(tontine.getMembers()));
    }
}
