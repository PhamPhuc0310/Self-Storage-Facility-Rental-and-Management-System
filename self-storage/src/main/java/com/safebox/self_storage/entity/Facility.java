package com.safebox.self_storage.entity;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "FACILITIES")
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "facility_id", updatable = false, nullable = false)
    private UUID facilityId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "opening_time")
    private LocalTime openingTime;

    @Column(name = "closing_time")
    private LocalTime closingTime;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "demo_intro") private String demoIntro;
    @Column(name = "demo_safety") private String demoSafety;
    @Column(name = "demo_access") private String demoAccess;
    @Column(name = "demo_terms") private String demoTerms;
    @Column(name = "image_path") private String imagePath;

    public UUID getFacilityId() { return facilityId; }
    public void setFacilityId(UUID facilityId) { this.facilityId = facilityId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public LocalTime getOpeningTime() { return openingTime; }
    public void setOpeningTime(LocalTime openingTime) { this.openingTime = openingTime; }
    public LocalTime getClosingTime() { return closingTime; }
    public void setClosingTime(LocalTime closingTime) { this.closingTime = closingTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDemoIntro() { return demoIntro; }
    public void setDemoIntro(String demoIntro) { this.demoIntro = demoIntro; }
    public String getDemoSafety() { return demoSafety; }
    public void setDemoSafety(String demoSafety) { this.demoSafety = demoSafety; }
    public String getDemoAccess() { return demoAccess; }
    public void setDemoAccess(String demoAccess) { this.demoAccess = demoAccess; }
    public String getDemoTerms() { return demoTerms; }
    public void setDemoTerms(String demoTerms) { this.demoTerms = demoTerms; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
}
