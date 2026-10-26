package com.safebox.self_storage.dto.support;
import jakarta.validation.constraints.*;
public record AddSupportMessage(@NotBlank @Size(max=4000) String message){}
