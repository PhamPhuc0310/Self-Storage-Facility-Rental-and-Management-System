package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.manager.MeasurementSubmissionRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.service.impl.UnitActivationService;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class UnitActivationController {
    private final UnitActivationService service;
    public UnitActivationController(UnitActivationService service) { this.service=service; }
    @GetMapping("/api/staff/measurements/facilities") public List<Facility> staffFacilities() { return service.staffFacilities(); }
    @GetMapping("/api/staff/measurements/facilities/{facilityId}/units") public List<StorageUnit> staffUnits(@PathVariable UUID facilityId) { return service.staffUnits(facilityId); }
    @GetMapping("/api/staff/measurements/facilities/{facilityId}/submissions") public List<UnitMeasurementSubmission> staffSubmissions(@PathVariable UUID facilityId) { return service.staffSubmissions(facilityId); }
    @PostMapping("/api/staff/measurements/facilities/{facilityId}/units/{unitId}") public UnitMeasurementSubmission submit(
            @PathVariable UUID facilityId,@PathVariable UUID unitId,@Valid @RequestBody MeasurementSubmissionRequest request) {
        return service.submit(facilityId,unitId,request);
    }
    @GetMapping("/api/manager/facilities/{facilityId}/measurements") public List<UnitMeasurementSubmission> reviews(@PathVariable UUID facilityId) { return service.reviews(facilityId); }
    public record DecisionRequest(String reason) {}
    @PostMapping("/api/manager/facilities/{facilityId}/measurements/{submissionId}/approve") public UnitMeasurementSubmission approve(
            @PathVariable UUID facilityId,@PathVariable UUID submissionId,@RequestBody DecisionRequest request) {
        return service.decide(facilityId,submissionId,true,request.reason());
    }
    @PostMapping("/api/manager/facilities/{facilityId}/measurements/{submissionId}/reject") public UnitMeasurementSubmission reject(
            @PathVariable UUID facilityId,@PathVariable UUID submissionId,@RequestBody DecisionRequest request) {
        return service.decide(facilityId,submissionId,false,request.reason());
    }
    @GetMapping("/api/manager/facilities/{facilityId}/units/{unitId}/activation-check") public UnitActivationService.ActivationCheck check(
            @PathVariable UUID facilityId,@PathVariable UUID unitId) { return service.check(facilityId,unitId); }
    @PostMapping("/api/manager/facilities/{facilityId}/units/{unitId}/activate") public StorageUnit activate(
            @PathVariable UUID facilityId,@PathVariable UUID unitId) { return service.activate(facilityId,unitId); }
}
