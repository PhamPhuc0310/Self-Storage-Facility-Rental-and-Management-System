package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.response.FacilityDetailResponse;
import com.safebox.self_storage.service.FacilityService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacilityDetailResponse> getFacilityDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(facilityService.getFacilityDetail(id));
    }
}
