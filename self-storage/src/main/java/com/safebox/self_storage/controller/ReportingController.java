package com.safebox.self_storage.controller;

import com.safebox.self_storage.service.ReportingService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {
    private final ReportingService reports;
    public ReportingController(ReportingService reports) { this.reports = reports; }

    private ReportingService.Filters filters(LocalDate startDate, LocalDate endDate, UUID facilityId) {
        return new ReportingService.Filters(startDate, endDate, facilityId);
    }
    @GetMapping("/financial-summary")
    public ReportingService.FinancialSummary financial(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam(required = false) UUID facilityId) {
        return reports.financial(filters(startDate,endDate,facilityId));
    }
    @GetMapping("/revenue-trend")
    public ReportingService.RevenueTrend trend(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam(required = false) UUID facilityId,
            @RequestParam(defaultValue = "DAY") String grouping) {
        return reports.trend(filters(startDate,endDate,facilityId),grouping);
    }
    @GetMapping("/facility-performance")
    public List<ReportingService.FacilityPerformance> performance(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam(required = false) UUID facilityId) {
        return reports.performance(filters(startDate,endDate,facilityId));
    }
    @GetMapping("/operational-summary")
    public ReportingService.OperationalSummary operational(@RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate, @RequestParam(required = false) UUID facilityId) {
        return reports.operational(filters(startDate,endDate,facilityId));
    }
}
