package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.manager.OverduePolicyProposalRequest;
import com.safebox.self_storage.entity.OverduePolicyProposal;
import com.safebox.self_storage.entity.RentalPolicy;
import com.safebox.self_storage.service.OverduePolicyGovernanceService;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class OverduePolicyGovernanceController {
    private final OverduePolicyGovernanceService service;
    public OverduePolicyGovernanceController(OverduePolicyGovernanceService service) { this.service=service; }
    public record DecisionRequest(String reason) {}
    @GetMapping("/api/business/overdue-policies/global")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public List<RentalPolicy> globalHistory() { return service.globalHistory(); }
    @PostMapping("/api/business/overdue-policies/global/activate")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public RentalPolicy activateGlobal(@RequestBody OverduePolicyProposalRequest request) { return service.activateGlobal(request); }
    @GetMapping("/api/manager/facilities/{facilityId}/overdue-policy-proposals")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public List<OverduePolicyProposal> managerList(@PathVariable UUID facilityId) { return service.list(facilityId); }
    @PostMapping("/api/manager/facilities/{facilityId}/overdue-policy-proposals")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public OverduePolicyProposal create(@PathVariable UUID facilityId,@RequestBody OverduePolicyProposalRequest request) { return service.create(facilityId,request); }
    @PutMapping("/api/manager/facilities/{facilityId}/overdue-policy-proposals/{proposalId}")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public OverduePolicyProposal edit(@PathVariable UUID facilityId,@PathVariable UUID proposalId,@RequestBody OverduePolicyProposalRequest request) { return service.edit(facilityId,proposalId,request); }
    @PostMapping("/api/manager/facilities/{facilityId}/overdue-policy-proposals/{proposalId}/submit")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public OverduePolicyProposal submit(@PathVariable UUID facilityId,@PathVariable UUID proposalId) { return service.submit(facilityId,proposalId); }
    @PostMapping("/api/manager/facilities/{facilityId}/overdue-policy-proposals/{proposalId}/withdraw")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public OverduePolicyProposal withdraw(@PathVariable UUID facilityId,@PathVariable UUID proposalId) { return service.withdraw(facilityId,proposalId); }
    @GetMapping("/api/business/facilities/{facilityId}/overdue-policy-proposals")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public List<OverduePolicyProposal> businessList(@PathVariable UUID facilityId) { return service.list(facilityId); }
    @PostMapping("/api/business/facilities/{facilityId}/overdue-policy-proposals/{proposalId}/approve")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public OverduePolicyProposal approve(@PathVariable UUID facilityId,@PathVariable UUID proposalId,@RequestBody DecisionRequest request) { return service.approve(facilityId,proposalId,request.reason()); }
    @PostMapping("/api/business/facilities/{facilityId}/overdue-policy-proposals/{proposalId}/reject")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public OverduePolicyProposal reject(@PathVariable UUID facilityId,@PathVariable UUID proposalId,@RequestBody DecisionRequest request) { return service.reject(facilityId,proposalId,request.reason()); }
}
