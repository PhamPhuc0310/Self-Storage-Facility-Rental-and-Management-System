package com.safebox.self_storage.dto;

import com.safebox.self_storage.entity.HandoverRecord;
import java.time.LocalDateTime;
import java.util.UUID;

public record HandoverView(UUID id, UUID contractId, UUID unitId, UUID staffId, String type,
                           LocalDateTime scheduledAt, LocalDateTime actualAt, String accessMethod,
                           String accessIdentifier, String customerSignature, String staffSignature,
                           String note, String status) {
    public static HandoverView from(HandoverRecord r) {
        return new HandoverView(r.getHandoverId(), r.getContractId(), r.getUnitId(), r.getStaffId(),
                r.getHandoverType(), r.getScheduledAt(), r.getActualAt(), r.getAccessMethod(),
                r.getAccessIdentifier(), r.getCustomerSignature(), r.getStaffSignature(), r.getNote(), r.getStatus());
    }
}
