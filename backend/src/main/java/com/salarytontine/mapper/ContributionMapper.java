package com.salarytontine.mapper;

import com.salarytontine.dto.response.ContributionResponse;
import com.salarytontine.entity.Contribution;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContributionMapper {

    public ContributionResponse toResponse(Contribution contribution) {
        return new ContributionResponse(
                contribution.getId(),
                contribution.getTontine().getId(),
                contribution.getTontine().getName(),
                contribution.getUser().getId(),
                contribution.getUser().getName(),
                contribution.getAmount(),
                contribution.getContributionMonth(),
                contribution.getPeriodIndex(),
                contribution.getPeriodStart(),
                contribution.getStatus());
    }

    public List<ContributionResponse> toResponses(List<Contribution> contributions) {
        return contributions.stream().map(this::toResponse).toList();
    }
}
