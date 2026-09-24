package com.safebox.storage.dto.response;

import java.util.UUID;

public record RegisterResponse(
        UUID userId,
        String fullName,
        String email,
        String role,
        String message
) {
}
