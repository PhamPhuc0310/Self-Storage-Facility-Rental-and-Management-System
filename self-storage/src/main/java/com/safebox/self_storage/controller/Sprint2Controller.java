package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.*;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.service.*;
import com.safebox.self_storage.repository.FacilityRepository;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class Sprint2Controller {
    private final PaymentService payments;
    private final AppointmentService appointments;
    private final ContractQueryService contracts;
    private final Sprint2Access access;
    private final FacilityRepository facilities;
    private final DepositRefundService refunds;
    public Sprint2Controller(PaymentService payments, AppointmentService appointments, ContractQueryService contracts,
            Sprint2Access access, FacilityRepository facilities, DepositRefundService refunds) {
        this.payments = payments; this.appointments = appointments; this.contracts = contracts;
        this.access = access; this.facilities = facilities; this.refunds = refunds;
    }
    public record AssignedFacility(UUID facilityId, String name, String address) {}
    @GetMapping("/api/staff/facilities")
    public List<AssignedFacility> staffFacilities() {
        return facilities.findAllById(access.facilities()).stream()
                .map(f -> new AssignedFacility(f.getFacilityId(), f.getName(), f.getAddress())).toList();
    }
    @GetMapping("/api/contracts/my")
    public List<ContractQueryService.ContractView> mine() { return contracts.mine(); }
    @GetMapping("/api/contracts/{id}")
    public ContractQueryService.ContractView contract(@PathVariable UUID id) { return contracts.get(id); }
    @GetMapping("/api/contracts/{id}/payments/summary")
    public PaymentService.Summary summary(@PathVariable UUID id) { return payments.summary(id); }
    @GetMapping("/api/contracts/{id}/payments")
    public List<PaymentView> history(@PathVariable UUID id) { return payments.history(id).stream().map(PaymentView::from).toList(); }
    @PostMapping("/api/contracts/{id}/payments")
    public ResponseEntity<PaymentView> pay(@PathVariable UUID id, @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentView.from(payments.pay(id, request)));
    }
    @PostMapping("/api/staff/contracts/{id}/payments/{paymentId}/confirm-transfer")
    public PaymentView confirm(@PathVariable UUID id, @PathVariable UUID paymentId) { return PaymentView.from(payments.confirmTransfer(id, paymentId)); }
    @GetMapping("/api/contracts/{id}/deposit-refunds")
    public List<DepositRefund> myRefunds(@PathVariable UUID id) { return refunds.mine(id); }
    @PostMapping("/api/contracts/{id}/deposit-refunds")
    public ResponseEntity<DepositRefund> requestRefund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(refunds.request(id, request));
    }
    @GetMapping("/api/manager/deposit-refunds")
    public List<DepositRefund> refundQueue() { return refunds.managerQueue(); }
    @PutMapping("/api/manager/deposit-refunds/{id}/review")
    public DepositRefund reviewRefund(@PathVariable UUID id, @Valid @RequestBody RefundReview request) { return refunds.review(id, request); }
    @PutMapping("/api/manager/deposit-refunds/{id}/complete")
    public DepositRefund completeRefund(@PathVariable UUID id, @Valid @RequestBody RefundCompletion request) { return refunds.complete(id, request); }
    @GetMapping("/api/contracts/{id}/pickup-appointment")
    public AppointmentView get(@PathVariable UUID id) { return appointments.view(appointments.get(id)); }
    @PostMapping("/api/contracts/{id}/pickup-appointment")
    public ResponseEntity<AppointmentView> create(@PathVariable UUID id, @Valid @RequestBody AppointmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(appointments.view(appointments.create(id, request)));
    }
    @PutMapping("/api/contracts/{id}/pickup-appointment")
    public AppointmentView reschedule(@PathVariable UUID id, @Valid @RequestBody AppointmentRequest request) { return appointments.view(appointments.reschedule(id, request)); }
    @DeleteMapping("/api/contracts/{id}/pickup-appointment")
    public AppointmentView cancel(@PathVariable UUID id) { return appointments.view(appointments.cancel(id)); }
    @GetMapping("/api/staff/tasks")
    public List<AppointmentService.DailyTask> daily(@RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String taskType, @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID staffId, @RequestParam(required = false) UUID facilityId) {
        return facilityId == null ? appointments.daily(date, taskType, status, staffId)
                : appointments.daily(date, taskType, status, staffId, facilityId);
    }
    @PutMapping("/api/manager/contracts/{id}/pickup-appointment/assignment")
    public AppointmentView assign(@PathVariable UUID id, @Valid @RequestBody AssignmentRequest request) {
        return appointments.view(appointments.assign(id, request.staffId(), request.reason()));
    }
    @GetMapping("/api/manager/staff")
    public List<AppointmentService.StaffOption> staff(@RequestParam(required = false) UUID facilityId) {
        return facilityId == null ? appointments.staffOptions() : appointments.staffOptions(facilityId);
    }
    @GetMapping("/api/manager/appointments/awaiting-assignment")
    public List<AppointmentService.DailyTask> awaitingAssignment(@RequestParam(required = false) UUID facilityId) {
        return appointments.awaitingAssignment(facilityId);
    }
    @GetMapping("/api/staff/tasks/upcoming")
    public List<AppointmentService.DailyTask> upcomingMine() { return appointments.upcomingMine(); }
}
