package com.salarytontine.controller;

import com.salarytontine.dto.response.DashboardResponse;
import com.salarytontine.dto.response.ScheduleEntryResponse;
import com.salarytontine.mapper.SalaryRecordMapper;
import com.salarytontine.mapper.TontineMapper;
import com.salarytontine.mapper.UserMapper;
import com.salarytontine.service.DashboardService;
import com.salarytontine.service.TontineCycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Tableau de bord", description = "Vue agregee de l'utilisateur authentifie")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserMapper userMapper;
    private final TontineMapper tontineMapper;
    private final SalaryRecordMapper salaryRecordMapper;

    public DashboardController(DashboardService dashboardService,
                               UserMapper userMapper,
                               TontineMapper tontineMapper,
                               SalaryRecordMapper salaryRecordMapper) {
        this.dashboardService = dashboardService;
        this.userMapper = userMapper;
        this.tontineMapper = tontineMapper;
        this.salaryRecordMapper = salaryRecordMapper;
    }

    @GetMapping
    @Operation(summary = "Tontine active, position dans le cycle et dernier salaire simule")
    public ResponseEntity<DashboardResponse> getDashboard() {
        DashboardService.DashboardData data = dashboardService.load();

        return ResponseEntity.ok(new DashboardResponse(
                userMapper.toResponse(data.user()),
                data.user().getBaseSalary(),
                data.activeTontine() == null ? null : tontineMapper.toResponse(data.activeTontine()),
                data.membership() == null ? null : data.membership().getTurnOrder(),
                data.myTurnDate(),
                toScheduleEntry(data.nextSlot()),
                data.latestSalaryRecord() == null
                        ? null : salaryRecordMapper.toResponse(data.latestSalaryRecord()),
                data.activeTontineCount(),
                data.myTurnPotAmount(),
                data.projectedTurnSalary()));
    }

    private ScheduleEntryResponse toScheduleEntry(TontineCycleService.CycleSlot slot) {
        if (slot == null) {
            return null;
        }
        return new ScheduleEntryResponse(
                slot.periodIndex(),
                slot.start(),
                slot.end(),
                slot.beneficiary().getUser().getId(),
                slot.beneficiary().getUser().getName(),
                slot.beneficiary().getTurnOrder());
    }
}
