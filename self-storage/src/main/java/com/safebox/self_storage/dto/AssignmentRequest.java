package com.safebox.self_storage.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignmentRequest(@NotNull UUID staffId, String reason) {}
