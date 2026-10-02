package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.response.EstimatedPriceResponse;
import com.safebox.self_storage.service.PricingService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @GetMapping("/estimate")
    public ResponseEntity<EstimatedPriceResponse> estimatePrice(
            @RequestParam("facilityId") UUID facilityId,
            @RequestParam("typeId") Integer typeId,
            @RequestParam("months") int months) {
            
        return ResponseEntity.ok(pricingService.estimatePrice(facilityId, typeId, months));
    }
}
