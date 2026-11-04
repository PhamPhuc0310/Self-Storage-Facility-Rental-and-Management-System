package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "STORAGE_UNITS")
public class StorageUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "unit_id", updatable = false, nullable = false)
    private UUID unitId;

    @Column(name = "facility_id", nullable = false)
    private UUID facilityId;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "unit_number", nullable = false, length = 50)
    private String unitNumber;

    @Column(name = "floor", length = 20)
    private String floor;

    @Column(name = "zone", length = 50)
    private String zone;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "length_m", precision = 8, scale = 3) private BigDecimal lengthM;
    @Column(name = "width_m", precision = 8, scale = 3) private BigDecimal widthM;
    @Column(name = "height_m", precision = 8, scale = 3) private BigDecimal heightM;
    @Column(name = "readiness_verified", nullable = false) private boolean readinessVerified;
    @Column(name = "dimensions_verified", nullable = false) private boolean dimensionsVerified;
    @Column(name = "measurement_source", length = 30) private String measurementSource;
    public String getMeasurementSource() { return measurementSource; }
    public void setMeasurementSource(String value) { measurementSource = value; }

    public BigDecimal getLengthM() { return lengthM; }
    public void setLengthM(BigDecimal value) { lengthM = value; }
    public BigDecimal getWidthM() { return widthM; }
    public void setWidthM(BigDecimal value) { widthM = value; }
    public BigDecimal getHeightM() { return heightM; }
    public void setHeightM(BigDecimal value) { heightM = value; }
    public boolean isReadinessVerified() { return readinessVerified; }
    public void setReadinessVerified(boolean value) { readinessVerified = value; }
    public boolean isDimensionsVerified() { return dimensionsVerified; }
    public void setDimensionsVerified(boolean value) { dimensionsVerified = value; }
    public BigDecimal getVolumeM3() { return !dimensionsVerified || lengthM == null || widthM == null || heightM == null ? null : lengthM.multiply(widthM).multiply(heightM); }

    public UUID getUnitId() { return unitId; }
    public void setUnitId(UUID unitId) { this.unitId = unitId; }
    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public Integer getTypeId() { return typeId; }
    public void setTypeId(Integer typeId) { this.typeId = typeId; }
    public String getUnitNumber() { return unitNumber; }
    public void setUnitNumber(String unitNumber) { this.unitNumber = unitNumber; }
    public String getFloor() { return floor; }
    public void setFloor(String floor) { this.floor = floor; }
    public String getZone() { return zone; }
    public void setZone(String zone) { this.zone = zone; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
