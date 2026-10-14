package com.safebox.self_storage.controller;

import com.safebox.self_storage.dto.*;
import com.safebox.self_storage.service.HandoverService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HandoverController {
    private final HandoverService handovers;
    public HandoverController(HandoverService handovers) { this.handovers = handovers; }

    @PostMapping("/staff/contracts/{contractId}/handover")
    public ResponseEntity<HandoverView> complete(@PathVariable UUID contractId,
            @Valid @RequestBody HandoverRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(HandoverView.from(handovers.complete(contractId, request)));
    }
}
