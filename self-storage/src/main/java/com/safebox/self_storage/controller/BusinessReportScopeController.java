package com.safebox.self_storage.controller;

import com.safebox.self_storage.service.BusinessReportScopeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/report-scopes")
public class BusinessReportScopeController {
    public record GrantRequest(@NotNull UUID facilityId) {}

    private final BusinessReportScopeService service;

    public BusinessReportScopeController(BusinessReportScopeService service) {
        this.service = service;
    }

    @GetMapping("/{managerId}")
    public List<UUID> list(@PathVariable UUID managerId) {
        return service.list(managerId);
    }

    @PostMapping("/{managerId}")
    public List<UUID> grant(@PathVariable UUID managerId, @Valid @RequestBody GrantRequest request) {
        return service.grant(managerId, request.facilityId());
    }

    @DeleteMapping("/{managerId}/{facilityId}")
    public List<UUID> revoke(@PathVariable UUID managerId, @PathVariable UUID facilityId) {
        return service.revoke(managerId, facilityId);
    }
}
