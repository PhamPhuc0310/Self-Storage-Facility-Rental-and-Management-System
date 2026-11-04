package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "UNIT_MEASUREMENT_SUBMISSIONS")
public class UnitMeasurementSubmission {
    @Id @Column(name="submission_id") public UUID submissionId;
    @Column(name="facility_id",nullable=false) public UUID facilityId;
    @Column(name="unit_id",nullable=false) public UUID unitId;
    @Column(name="length_m",nullable=false,precision=8,scale=3) public BigDecimal lengthM;
    @Column(name="width_m",nullable=false,precision=8,scale=3) public BigDecimal widthM;
    @Column(name="height_m",nullable=false,precision=8,scale=3) public BigDecimal heightM;
    @Column(name="condition_note",nullable=false,length=1000) public String conditionNote;
    @Column(name="source",nullable=false,length=30) public String source;
    @Column(name="status",nullable=false,length=20) public String status;
    @Column(name="submitted_by",nullable=false) public UUID submittedBy;
    @Column(name="submitted_at",nullable=false) public LocalDateTime submittedAt;
    @Column(name="reviewed_by") public UUID reviewedBy;
    @Column(name="reviewed_at") public LocalDateTime reviewedAt;
    @Column(name="decision_reason",length=1000) public String decisionReason;
}
