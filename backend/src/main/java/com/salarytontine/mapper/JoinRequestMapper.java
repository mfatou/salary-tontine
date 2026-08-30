package com.salarytontine.mapper;

import com.salarytontine.dto.response.JoinRequestResponse;
import com.salarytontine.entity.TontineJoinRequest;
import com.salarytontine.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class JoinRequestMapper {

    public JoinRequestResponse toResponse(TontineJoinRequest request) {
        User decidedBy = request.getDecidedBy();
        return new JoinRequestResponse(
                request.getId(),
                request.getTontine().getId(),
                request.getTontine().getName(),
                request.getUser().getId(),
                request.getUser().getName(),
                request.getUser().getEmail(),
                request.getStatus(),
                request.getMotivation(),
                request.getDecisionNote(),
                request.getRequestedAt(),
                request.getDecidedAt(),
                decidedBy == null ? null : decidedBy.getName());
    }

    public List<JoinRequestResponse> toResponses(List<TontineJoinRequest> requests) {
        return requests.stream().map(this::toResponse).toList();
    }
}
