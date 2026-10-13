package com.safebox.self_storage.controller;

import com.safebox.self_storage.service.ContractQueryService;
import com.safebox.self_storage.service.ReservationApprovalService;
import com.safebox.self_storage.dto.RejectReservationRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager/reservations")
public class ReservationApprovalController {
    private final ReservationApprovalService approval;
    private final ContractQueryService contracts;
    public ReservationApprovalController(ReservationApprovalService approval, ContractQueryService contracts) {
        this.approval = approval; this.contracts = contracts;
    }
    @GetMapping("/pending")
    public List<ReservationApprovalService.Review> pending() { return approval.pending(); }
    @PostMapping("/{id}/confirm")
    public ContractQueryService.ContractView confirm(@PathVariable UUID id) {
        return contracts.get(approval.approve(id).contractId);
    }
    public record RejectResult(UUID reservationId, String status) {}
    @PostMapping("/{id}/reject")
    public RejectResult reject(@PathVariable UUID id, @RequestBody(required = false) RejectReservationRequest request) {
        approval.reject(id, request);
        return new RejectResult(id, "REJECTED");
    }
}
