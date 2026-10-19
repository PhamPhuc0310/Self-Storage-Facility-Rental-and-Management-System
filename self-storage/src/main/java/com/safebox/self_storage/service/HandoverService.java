package com.safebox.self_storage.service;

import com.safebox.self_storage.dto.HandoverRequest;
import com.safebox.self_storage.entity.*;
import com.safebox.self_storage.exception.BusinessException;
import com.safebox.self_storage.repository.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HandoverService {
    private static final String TYPE = "CHECK_IN";
    private final Sprint2Access access;
    private final PickupAppointmentRepository appointments;
    private final HandoverRecordRepository handovers;
    private final InspectionReportRepository inspections;
    private final RentalContractRepository contracts;
    private final StorageUnitRepository units;
    private final PaymentService payments;

    public HandoverService(Sprint2Access access, PickupAppointmentRepository appointments,
            HandoverRecordRepository handovers, InspectionReportRepository inspections,
            RentalContractRepository contracts, StorageUnitRepository units, PaymentService payments) {
        this.access = access; this.appointments = appointments; this.handovers = handovers;
        this.inspections = inspections; this.contracts = contracts; this.units = units; this.payments = payments;
    }

    @Transactional
    public HandoverRecord complete(UUID contractId, HandoverRequest request) {
        RentalContract contract = access.contract(contractId, true);
        if (!"FACILITY_STAFF".equals(access.role()) && !"FACILITY_MANAGER".equals(access.role()))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Chỉ nhân viên cơ sở hoặc quản lý mới được bàn giao kho.");

        HandoverRecord existing = handovers.findFirstByContractIdAndHandoverTypeAndStatus(contractId, TYPE, "COMPLETED").orElse(null);
        if (existing != null) return existing; // BR14.8 idempotency
        access.confirmed(contract);
        PickupAppointment appointment = appointments.findFirstByContractIdAndStatus(contractId, "SCHEDULED")
                .orElseThrow(() -> new BusinessException(HttpStatus.CONFLICT, "Hợp đồng chưa có lịch nhận kho đang chờ bàn giao."));
        UUID facilityId = access.facilityId(contract);
        if (!facilityId.equals(appointment.facilityId))
            throw new BusinessException(HttpStatus.CONFLICT, "Lịch nhận kho không khớp cơ sở của hợp đồng.");
        if ("FACILITY_STAFF".equals(access.role()) && !access.currentId().equals(appointment.assignedStaffId))
            throw new BusinessException(HttpStatus.FORBIDDEN, "Bạn chỉ được thực hiện công việc đã được giao.");
        if (!payments.summary(contractId).readyForHandover())
            throw new BusinessException(HttpStatus.CONFLICT, "Chưa thu đủ tiền cọc và tiền thuê ban đầu.");
        LocalDateTime now = access.now();
        if (now.toLocalDate().isBefore(contract.startDate) || now.toLocalDate().isAfter(contract.endDate))
            throw new BusinessException(HttpStatus.CONFLICT, "Thời điểm bàn giao không nằm trong thời hạn thuê.");
        StorageUnit unit = units.lockById(contract.unitId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "Không tìm thấy kho đã gán cho hợp đồng."));
        if (!facilityId.equals(unit.getFacilityId()))
            throw new BusinessException(HttpStatus.CONFLICT, "Kho không thuộc đúng cơ sở của hợp đồng.");
        // Approval reserved this exact unit for the contract. An AVAILABLE unit
        // has not passed through the allocation step and must not be handed over.
        if (!"RESERVED".equals(unit.getStatus()))
            throw new BusinessException(HttpStatus.CONFLICT, "Kho chưa được giữ chỗ cho hợp đồng này.");

        HandoverRecord record = new HandoverRecord();
        record.setContractId(contractId); record.setUnitId(unit.getUnitId()); record.setStaffId(access.currentId());
        record.setHandoverType(TYPE); record.setScheduledAt(appointment.appointmentTime); record.setActualAt(now);
        record.setAccessMethod(request.accessMethod().trim()); record.setAccessIdentifier(request.accessIdentifier().trim());
        record.setCustomerSignature(request.customerSignature().trim()); record.setStaffSignature(request.staffSignature());
        record.setNote(request.note()); record.setStatus("COMPLETED");
        HandoverRecord saved = handovers.saveAndFlush(record);

        InspectionReport inspection = new InspectionReport();
        inspection.setUnitId(unit.getUnitId()); inspection.setContractId(contractId); inspection.setInspectedBy(access.currentId());
        inspection.setInspectionType(TYPE); inspection.setInspectionDate(now); inspection.setConditionStatus(request.conditionStatus().trim());
        inspection.setTemperature(request.temperature()); inspection.setHumidity(request.humidity());
        inspection.setDamageDescription(null); inspection.setEstimatedRepairCost(request.estimatedRepairCost());
        inspection.setCustomerConfirmed(true); inspection.setNote(request.note()); inspections.save(inspection);

        contract.status = "ACTIVE"; contract.actualStartDate = now.toLocalDate(); contracts.save(contract);
        unit.setStatus("OCCUPIED"); units.save(unit);
        appointment.status = "COMPLETED"; appointment.updatedAt = now; appointments.save(appointment);
        return saved;
    }
}
