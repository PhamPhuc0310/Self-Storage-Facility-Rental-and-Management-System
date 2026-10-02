package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.response.FacilityDetailResponse;
import com.safebox.self_storage.dto.response.FacilityListResponse;
import com.safebox.self_storage.service.FacilityService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/facilities")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping
    public ResponseEntity<Page<FacilityListResponse>> getFacilities(
            @RequestParam(value = "search", required = false) String search,
            Pageable pageable) {
        return ResponseEntity.ok(facilityService.getActiveFacilities(search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FacilityDetailResponse> getFacilityDetail(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(facilityService.getFacilityDetail(id));
    }
}
