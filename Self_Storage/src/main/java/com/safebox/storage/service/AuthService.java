package com.safebox.storage.service;

import com.safebox.storage.dto.request.LoginRequest;
import com.safebox.storage.dto.response.AuthenticatedUserResponse;
import com.safebox.storage.dto.response.LoginResponse;
import com.safebox.storage.dto.response.LogoutResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    AuthenticatedUserResponse getAuthenticatedUser(String email);

    LogoutResponse logout();
}
