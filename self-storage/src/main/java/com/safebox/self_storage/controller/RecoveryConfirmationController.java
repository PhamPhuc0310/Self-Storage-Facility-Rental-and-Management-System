package com.safebox.self_storage.controller;

import com.safebox.self_storage.service.RecoveryConfirmationService;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/manager/overdue/contracts")
public class RecoveryConfirmationController {
    private final RecoveryConfirmationService service;
    public RecoveryConfirmationController(RecoveryConfirmationService service) { this.service=service; }
    @PostMapping("/{contractId}/recovery-confirmation")
    public RecoveryConfirmationService.Result confirm(@PathVariable UUID contractId,
            @RequestBody RecoveryConfirmationService.Request request) {
        Object principal=SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return service.confirm(principal instanceof UUID id ? id : UUID.fromString(principal.toString()),contractId,request);
    }
}
