package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.response.AvailabilityResponse;
import com.safebox.self_storage.service.AvailabilityService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/availability")
public class AvailabilityController {
    private final AvailabilityService availability;
    public AvailabilityController(AvailabilityService availability) { this.availability = availability; }
    @GetMapping
    public AvailabilityResponse check(@RequestParam UUID facilityId, @RequestParam Integer typeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return availability.check(facilityId, typeId, startDate, endDate);
    }
}
