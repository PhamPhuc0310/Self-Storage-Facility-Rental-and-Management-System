package com.safebox.self_storage.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record AppointmentView(UUID id, UUID contractId, LocalDateTime appointmentTime,
                              UUID assignedStaffId, String assignedStaffName, String status) {}
