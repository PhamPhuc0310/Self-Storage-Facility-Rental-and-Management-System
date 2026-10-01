package com.safebox.self_storage.dto;

import com.safebox.self_storage.entity.User;
import java.util.UUID;

public record UserView(UUID userId, String fullName, String email, String role) {
    public static UserView from(User user) {
        return new UserView(user.getUserId(), user.getFullName(), user.getEmail(), user.getRole().getRoleName());
    }
}
