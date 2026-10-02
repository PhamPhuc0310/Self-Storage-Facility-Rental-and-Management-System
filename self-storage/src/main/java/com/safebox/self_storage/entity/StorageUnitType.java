package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "STORAGE_UNIT_TYPES")
public class StorageUnitType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "type_id", updatable = false, nullable = false)
    private Integer typeId;

    @Column(name = "type_name", nullable = false, length = 100)
    private String typeName;

    @Column(name = "storage_mode", nullable = false, length = 30)
    private String storageMode;

    @Column(name = "size_name", nullable = false, length = 50)
    private String sizeName;

    @Column(name = "width", nullable = false, precision = 6, scale = 2)
    private BigDecimal width;

    @Column(name = "length", nullable = false, precision = 6, scale = 2)
    private BigDecimal length;

    @Column(name = "height", nullable = false, precision = 6, scale = 2)
    private BigDecimal height;

    @Column(name = "features")
    private String features;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public Integer getTypeId() { return typeId; }
    public void setTypeId(Integer typeId) { this.typeId = typeId; }
    public String getTypeName() { return typeName; }
    public void setTypeName(String typeName) { this.typeName = typeName; }
    public String getStorageMode() { return storageMode; }
    public void setStorageMode(String storageMode) { this.storageMode = storageMode; }
    public String getSizeName() { return sizeName; }
    public void setSizeName(String sizeName) { this.sizeName = sizeName; }
    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }
    public BigDecimal getLength() { return length; }
    public void setLength(BigDecimal length) { this.length = length; }
    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
