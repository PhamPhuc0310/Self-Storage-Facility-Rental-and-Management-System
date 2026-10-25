package com.safebox.self_storage.controller;

import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.service.EarlyReturnService;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class EarlyReturnController {
    private final EarlyReturnService service;
    public EarlyReturnController(EarlyReturnService service) { this.service = service; }

    @PostMapping("/api/customer/contracts/{contractId}/early-return")
    public ResponseEntity<TerminationRequest> request(@PathVariable UUID contractId,
            @RequestBody EarlyReturnService.RequestInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.request(contractId, input));
    }
    @GetMapping("/api/customer/early-returns")
    public List<TerminationRequest> mine() { return service.customerList(); }
    @GetMapping("/api/customer/early-returns/{id}")
    public TerminationRequest mine(@PathVariable UUID id) { return service.customerGet(id); }
    @GetMapping("/api/manager/early-returns")
    public List<TerminationRequest> managerList() { return service.managerList(); }
    @GetMapping("/api/manager/early-returns/{id}/settlement")
    public EarlyReturnService.SettlementView settlement(@PathVariable UUID id) { return service.settlement(id); }
    @PostMapping("/api/manager/early-returns/{id}/settlement/approve")
    public EarlyReturnSettlement approve(@PathVariable UUID id, @RequestBody EarlyReturnService.ApprovalInput input) {
        return service.approve(id, input);
    }
    @PostMapping("/api/manager/early-returns/{id}/settlement/reject")
    public TerminationRequest reject(@PathVariable UUID id, @RequestBody EarlyReturnService.RejectInput input) {
        return service.reject(id, input);
    }
    @PostMapping("/api/manager/early-returns/{id}/refund")
    public EarlyReturnSettlement refund(@PathVariable UUID id) { return service.refund(id); }
}
