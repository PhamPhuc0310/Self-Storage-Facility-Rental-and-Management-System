package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PICKUP_APPOINTMENTS")
public class PickupAppointment {
    @Id @Column(name = "appointment_id") public UUID id;
    @Column(name = "contract_id", nullable = false) public UUID contractId;
    @Column(name = "facility_id", nullable = false) public UUID facilityId;
    @Column(name = "customer_id", nullable = false) public UUID customerId;
    @Column(name = "appointment_time", nullable = false) public LocalDateTime appointmentTime;
    @Column(name = "assigned_staff_id") public UUID assignedStaffId;
    @Column(name = "status", nullable = false) public String status;
    @Column(name = "created_at", nullable = false) public LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) public LocalDateTime updatedAt;
}
