package com.safebox.self_storage.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/role")
public class RoleProbeController {
    @GetMapping("/{role}")
    public Map<String, String> role(@PathVariable String role) {
        return Map.of("role", role);
    }
}
