package com.safebox.self_storage.dto.support;
import jakarta.validation.constraints.NotNull; import java.util.UUID;
public record AssignSupportRequest(@NotNull UUID staffId){}
