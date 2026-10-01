package com.safebox.self_storage.dto;

public record LoginResponse(String token, String tokenType, UserView user) {}
