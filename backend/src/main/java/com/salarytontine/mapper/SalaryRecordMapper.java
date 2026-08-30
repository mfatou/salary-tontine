package com.salarytontine.mapper;

import com.salarytontine.dto.response.SalaryRecordResponse;
import com.salarytontine.entity.SalaryRecord;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SalaryRecordMapper {

    public SalaryRecordResponse toResponse(SalaryRecord record) {
        return new SalaryRecordResponse(
                record.getId(),
                record.getUser().getId(),
                record.getUser().getName(),
                record.getTontine().getId(),
                record.getTontine().getName(),
                record.getSalaryMonth(),
                record.getBaseSalary(),
                record.getTontineDeduction(),
                record.getTontineReceived(),
                record.getFinalSalary(),
                record.isBeneficiary());
    }

    public List<SalaryRecordResponse> toResponses(List<SalaryRecord> records) {
        return records.stream().map(this::toResponse).toList();
    }
}
