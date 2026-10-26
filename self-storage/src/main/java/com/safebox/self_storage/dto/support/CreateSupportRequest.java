package com.safebox.self_storage.dto.support;
import jakarta.validation.constraints.*; import java.util.UUID;
public record CreateSupportRequest(@NotNull UUID contractId,@NotBlank @Pattern(regexp="ACCESS|PAYMENT|UNIT_CONDITION|GOODS|OTHER") String requestType,@NotBlank @Size(max=255) String title,@NotBlank @Size(max=4000) String description,@NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH") String priority){}
