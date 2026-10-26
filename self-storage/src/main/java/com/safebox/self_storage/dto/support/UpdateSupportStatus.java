package com.safebox.self_storage.dto.support;
import jakarta.validation.constraints.*;
public record UpdateSupportStatus(@NotBlank @Pattern(regexp="OPEN|IN_PROGRESS|RESOLVED|CLOSED") String status,@Size(max=4000) String note){}
