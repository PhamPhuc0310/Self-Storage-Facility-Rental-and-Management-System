package com.safebox.storage.service;

import com.safebox.storage.dto.request.LoginRequest;
import com.safebox.storage.dto.request.RegisterRequest;
import com.safebox.storage.dto.response.AuthenticatedUserResponse;
import com.safebox.storage.dto.response.LoginResponse;
import com.safebox.storage.dto.response.LogoutResponse;
import com.safebox.storage.dto.response.RegisterResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    RegisterResponse register(RegisterRequest request);

    AuthenticatedUserResponse getAuthenticatedUser(String email);

    LogoutResponse logout();
}
