package com.safebox.self_storage.controller;

import com.safebox.self_storage.service.impl.StorageDiscoveryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/discovery/units")
public class StorageDiscoveryController {
    private final StorageDiscoveryService service;
    public StorageDiscoveryController(StorageDiscoveryService service){this.service=service;}
    @GetMapping public List<StorageDiscoveryService.UnitCard> search(
            @RequestParam(required=false) UUID facilityId,@RequestParam(required=false) Integer typeId,
            @RequestParam(required=false) BigDecimal minVolumeM3,@RequestParam(required=false) BigDecimal maxVolumeM3,
            @RequestParam(required=false) BigDecimal minPrice,@RequestParam(required=false) BigDecimal maxPrice,
            @RequestParam(required=false) String availability,@RequestParam(required=false) LocalDate startDate,
            @RequestParam(required=false) Integer months) {
        return service.search(new StorageDiscoveryService.Filters(facilityId,typeId,minVolumeM3,maxVolumeM3,minPrice,maxPrice,availability,startDate,months));
    }
}
