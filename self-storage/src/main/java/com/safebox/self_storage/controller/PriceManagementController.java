package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.manager.PriceScheduleRequest;
import com.safebox.self_storage.entity.PriceProposal;
import com.safebox.self_storage.entity.PricingPolicy;
import com.safebox.self_storage.entity.StorageUnitType;
import com.safebox.self_storage.repository.StorageUnitTypeRepository;
import com.safebox.self_storage.service.PriceManagementService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class PriceManagementController {
    private final PriceManagementService service;
    private final StorageUnitTypeRepository types;
    public PriceManagementController(PriceManagementService service, StorageUnitTypeRepository types) { this.service=service; this.types=types; }
    public record ProposalRequest(PriceScheduleRequest schedule, String reason) {}

    @GetMapping("/api/business/unit-types")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public List<StorageUnitType> activeTypes() { return types.findAll().stream().filter(t -> "ACTIVE".equals(t.getStatus())).toList(); }

    @GetMapping("/api/business/facilities/{facilityId}/prices")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public List<PricingPolicy> businessPrices(@PathVariable UUID facilityId) { return service.prices(facilityId); }
    @PostMapping("/api/business/facilities/{facilityId}/prices")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public PricingPolicy create(@PathVariable UUID facilityId, @RequestBody PriceScheduleRequest r) { return service.createDraft(facilityId,r); }
    @PutMapping("/api/business/facilities/{facilityId}/prices/{priceId}")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public PricingPolicy edit(@PathVariable UUID facilityId, @PathVariable UUID priceId, @RequestBody PriceScheduleRequest r) { return service.editDraft(facilityId,priceId,r); }
    @PostMapping("/api/business/facilities/{facilityId}/prices/{priceId}/activate")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public PricingPolicy activate(@PathVariable UUID facilityId, @PathVariable UUID priceId) { return service.activate(facilityId,priceId); }
    @PostMapping("/api/business/facilities/{facilityId}/prices/{priceId}/retire")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public PricingPolicy retire(@PathVariable UUID facilityId, @PathVariable UUID priceId) { return service.retire(facilityId,priceId); }
    @GetMapping("/api/manager/facilities/{facilityId}/prices")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public List<PricingPolicy> managerPrices(@PathVariable UUID facilityId) { return service.prices(facilityId); }
    @GetMapping("/api/manager/facilities/{facilityId}/price-proposals")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public List<PriceProposal> managerProposals(@PathVariable UUID facilityId) { return service.proposals(facilityId); }
    @PostMapping("/api/manager/facilities/{facilityId}/price-proposals")
    @PreAuthorize("hasRole('FACILITY_MANAGER')")
    public PriceProposal submit(@PathVariable UUID facilityId, @RequestBody ProposalRequest r) { return service.submit(facilityId,r.schedule(),r.reason()); }
    @GetMapping("/api/business/facilities/{facilityId}/price-proposals")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public List<PriceProposal> businessProposals(@PathVariable UUID facilityId) { return service.proposals(facilityId); }
    @PostMapping("/api/business/price-proposals/{id}/apply")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public PriceProposal apply(@PathVariable UUID id) { return service.applyProposal(id); }
    @PostMapping("/api/business/price-proposals/{id}/reject")
    @PreAuthorize("hasRole('BUSINESS_OPERATIONS_MANAGER')")
    public PriceProposal reject(@PathVariable UUID id) { return service.reject(id); }
}
