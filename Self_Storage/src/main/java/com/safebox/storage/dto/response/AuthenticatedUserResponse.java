package com.safebox.storage.dto.response;

import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID userId,
        String fullName,
        String email,
        String role
) {
}
