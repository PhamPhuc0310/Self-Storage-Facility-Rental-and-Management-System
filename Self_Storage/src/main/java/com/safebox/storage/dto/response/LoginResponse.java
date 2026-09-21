package com.safebox.storage.dto.response;

public record LoginResponse(
        String token,
        String tokenType,
        AuthenticatedUserResponse user
) {
}
